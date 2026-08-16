param(
    [int]$RuntimeTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runId = [guid]::NewGuid().ToString("N").Substring(0, 10)
$ownedProcesses = @()
$fatalError = $null

$results = [ordered]@{
    "Full Maven reactor" = $null
    "Ledger malformed payload -> DLT" = $null
    "Ledger DB failure -> retry/DLT" = $null
    "Fraud malformed payload -> DLT" = $null
    "Fraud DB failure -> retry/DLT" = $null
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Test-HttpHealth {
    param(
        [string]$Url,
        [int]$TimeoutSeconds
    )

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        & curl.exe -fsS $Url *> $null
        if ($LASTEXITCODE -eq 0) {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-KafkaReady {
    param([int]$TimeoutSeconds = 120)

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        & docker exec nexapay-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Kafka did not become ready within $TimeoutSeconds seconds."
}

function Wait-PostgresReady {
    param(
        [string]$Container,
        [string]$Database,
        [int]$TimeoutSeconds = 120
    )

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        & docker exec $Container pg_isready -U nexapay -d $Database *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "$Container did not become ready within $TimeoutSeconds seconds."
}

function Send-KafkaMessage {
    param(
        [string]$Topic,
        [string]$Payload
    )

    $Payload | & docker exec -i nexapay-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic $Topic
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to publish message to $Topic."
    }
}

function Wait-TopicContains {
    param(
        [string]$Topic,
        [string]$Marker,
        [int]$TimeoutSeconds
    )

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $output = & docker exec nexapay-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic $Topic --from-beginning --timeout-ms 1000 2>$null
        $text = ($output -join "`n")
        if ($text.Contains($Marker)) {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Start-ServiceIfNeeded {
    param(
        [string]$Module,
        [string]$HealthUrl,
        [string]$Label
    )

    if (Test-HttpHealth -Url $HealthUrl -TimeoutSeconds 4) {
        Write-Host "[$Label] already running; reusing current process."
        return $null
    }

    $mvnPath = (Get-Command mvn).Source
    $stdout = Join-Path $env:TEMP ("nexapay-{0}-sprint6-{1}.out.log" -f $Label.ToLower(), $runId)
    $stderr = Join-Path $env:TEMP ("nexapay-{0}-sprint6-{1}.err.log" -f $Label.ToLower(), $runId)

    $oldTimeout = $env:SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT
    try {
        $env:SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT = "3000"
        $process = Start-Process -FilePath $mvnPath `
            -ArgumentList @("-B", "-pl", $Module, "spring-boot:run") `
            -WorkingDirectory $repoRoot `
            -RedirectStandardOutput $stdout `
            -RedirectStandardError $stderr `
            -PassThru
    }
    finally {
        if ($null -eq $oldTimeout) {
            Remove-Item Env:SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT -ErrorAction SilentlyContinue
        }
        else {
            $env:SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT = $oldTimeout
        }
    }

    if (-not (Test-HttpHealth -Url $HealthUrl -TimeoutSeconds 120)) {
        Write-Host "--- $Label stdout ---"
        if (Test-Path $stdout) { Get-Content $stdout -Tail 120 }
        Write-Host "--- $Label stderr ---"
        if (Test-Path $stderr) { Get-Content $stderr -Tail 120 }
        try { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue } catch {}
        throw "$Label did not become healthy."
    }

    Write-Host "[$Label] started by validation script. PID=$($process.Id)"
    return $process
}

function Invoke-Check {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    Write-Host "`n=== $Name ==="
    try {
        & $Action
        $script:results[$Name] = $true
        Write-Host "[PASS] $Name"
    }
    catch {
        $script:results[$Name] = $false
        Write-Host "[FAIL] $Name"
        Write-Host $_.Exception.Message
    }
}

Push-Location $repoRoot
try {
    Require-Command "mvn"
    Require-Command "docker"
    Require-Command "curl.exe"

    Invoke-Check "Full Maven reactor" {
        & mvn -B clean test
        if ($LASTEXITCODE -ne 0) {
            throw "mvn clean test returned exit code $LASTEXITCODE."
        }
    }

    if ($results["Full Maven reactor"] -ne $true) {
        throw "Full Maven reactor failed; runtime resilience validation was skipped."
    }

    Write-Host "`n=== Starting Kafka and resilience databases ==="
    & docker compose up -d kafka postgres-ledger postgres-fraud
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed."
    }

    Wait-KafkaReady
    Wait-PostgresReady -Container "nexapay-ledger-postgres" -Database "nexapay_ledger"
    Wait-PostgresReady -Container "nexapay-fraud-postgres" -Database "nexapay_fraud"

    $ledgerProcess = Start-ServiceIfNeeded -Module "ledger-service" -HealthUrl "http://localhost:8083/actuator/health" -Label "Ledger"
    if ($null -ne $ledgerProcess) { $ownedProcesses += $ledgerProcess }

    Invoke-Check "Ledger malformed payload -> DLT" {
        $marker = "not-json-ledger-sprint6-$runId"
        Send-KafkaMessage -Topic "nexapay.account.credited.v1" -Payload $marker
        if (-not (Wait-TopicContains -Topic "nexapay.account.credited.v1.DLT" -Marker $marker -TimeoutSeconds 45)) {
            throw "Malformed Ledger payload did not reach DLT."
        }
    }

    Invoke-Check "Ledger DB failure -> retry/DLT" {
        $marker = "ACC-S6-LEDGER-$runId"
        & docker stop nexapay-ledger-postgres | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not stop Ledger PostgreSQL." }

        try {
            $payload = @{
                eventId = [guid]::NewGuid().ToString()
                accountId = [guid]::NewGuid().ToString()
                accountNumber = $marker
                amount = 700.00
                balanceAfter = 700.00
                occurredAt = [DateTimeOffset]::UtcNow.ToString("o")
            } | ConvertTo-Json -Compress

            Send-KafkaMessage -Topic "nexapay.account.credited.v1" -Payload $payload

            if (-not (Wait-TopicContains -Topic "nexapay.account.credited.v1" -Marker $marker -TimeoutSeconds 30)) {
                throw "Ledger retry event was not found on the original topic."
            }

            if (-not (Wait-TopicContains -Topic "nexapay.account.credited.v1.DLT" -Marker $marker -TimeoutSeconds $RuntimeTimeoutSeconds)) {
                throw "Ledger retry event did not reach DLT after database failure."
            }
        }
        finally {
            & docker start nexapay-ledger-postgres | Out-Null
            Wait-PostgresReady -Container "nexapay-ledger-postgres" -Database "nexapay_ledger"
        }
    }

    $fraudProcess = Start-ServiceIfNeeded -Module "fraud-service" -HealthUrl "http://localhost:8084/actuator/health" -Label "Fraud"
    if ($null -ne $fraudProcess) { $ownedProcesses += $fraudProcess }

    Invoke-Check "Fraud malformed payload -> DLT" {
        $marker = "not-json-fraud-sprint6-$runId"
        Send-KafkaMessage -Topic "nexapay.payment.created.v1" -Payload $marker
        if (-not (Wait-TopicContains -Topic "nexapay.payment.created.v1.DLT" -Marker $marker -TimeoutSeconds 45)) {
            throw "Malformed Fraud payload did not reach DLT."
        }
    }

    Invoke-Check "Fraud DB failure -> retry/DLT" {
        $marker = "ACC-S6-FRAUD-$runId"
        & docker stop nexapay-fraud-postgres | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not stop Fraud PostgreSQL." }

        try {
            $payload = @{
                eventId = [guid]::NewGuid().ToString()
                paymentId = [guid]::NewGuid().ToString()
                payerAccountId = $marker
                pixKey = "sprint6-$runId@nexapay.test"
                amount = 12000.00
                occurredAt = [DateTimeOffset]::UtcNow.ToString("o")
            } | ConvertTo-Json -Compress

            Send-KafkaMessage -Topic "nexapay.payment.created.v1" -Payload $payload

            if (-not (Wait-TopicContains -Topic "nexapay.payment.created.v1" -Marker $marker -TimeoutSeconds 30)) {
                throw "Fraud retry event was not found on the original topic."
            }

            if (-not (Wait-TopicContains -Topic "nexapay.payment.created.v1.DLT" -Marker $marker -TimeoutSeconds $RuntimeTimeoutSeconds)) {
                throw "Fraud retry event did not reach DLT after database failure."
            }
        }
        finally {
            & docker start nexapay-fraud-postgres | Out-Null
            Wait-PostgresReady -Container "nexapay-fraud-postgres" -Database "nexapay_fraud"
        }
    }
}
catch {
    $fatalError = $_.Exception.Message
    Write-Host "`n[FATAL] $fatalError"
}
finally {
    & docker start nexapay-ledger-postgres *> $null
    & docker start nexapay-fraud-postgres *> $null

    foreach ($process in $ownedProcesses) {
        try {
            if (-not $process.HasExited) {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            }
        }
        catch {}
    }

    Pop-Location
}

Write-Host "`n========================================"
Write-Host "       NEXAPAY SPRINT 6 VALIDATION"
Write-Host "========================================"
foreach ($name in $results.Keys) {
    $value = $results[$name]
    if ($value -eq $true) { $status = "PASS" }
    elseif ($value -eq $false) { $status = "FAIL" }
    else { $status = "SKIP" }
    Write-Host ("{0,-42} {1}" -f $name, $status)
}

if ($null -ne $fatalError) {
    Write-Host "Fatal: $fatalError"
}

$failed = $false
foreach ($value in $results.Values) {
    if ($value -eq $false) { $failed = $true }
}
if ($null -ne $fatalError) { $failed = $true }

if ($failed) {
    exit 1
}

exit 0
