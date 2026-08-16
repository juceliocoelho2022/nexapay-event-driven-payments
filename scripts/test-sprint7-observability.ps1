param(
    [int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runId = [guid]::NewGuid().ToString("N").Substring(0, 10)
$ownedProcesses = @()
$fatalError = $null

$results = [ordered]@{
    "Full Maven reactor" = $null
    "Payment Prometheus endpoint" = $null
    "Account Prometheus endpoint" = $null
    "Ledger Prometheus endpoint" = $null
    "Fraud Prometheus endpoint" = $null
    "Auth Prometheus endpoint" = $null
    "Prometheus scrapes 5 services" = $null
    "Grafana datasource and dashboard" = $null
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
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $true
            }
        }
        catch {
        }

        Start-Sleep -Seconds 2
    }

    return $false
}

function Wait-KafkaReady {
    param([int]$TimeoutSeconds = 120)

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "SilentlyContinue"
            & docker exec nexapay-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list *> $null
            $exitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousPreference
        }

        if ($exitCode -eq 0) {
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
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "SilentlyContinue"
            & docker exec $Container pg_isready -U nexapay -d $Database *> $null
            $exitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousPreference
        }

        if ($exitCode -eq 0) {
            return
        }

        Start-Sleep -Seconds 2
    }

    throw "$Container did not become ready within $TimeoutSeconds seconds."
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
    $stdout = Join-Path $env:TEMP ("nexapay-{0}-sprint7-{1}.out.log" -f $Label.ToLower(), $runId)
    $stderr = Join-Path $env:TEMP ("nexapay-{0}-sprint7-{1}.err.log" -f $Label.ToLower(), $runId)

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

    if (-not (Test-HttpHealth -Url $HealthUrl -TimeoutSeconds $StartupTimeoutSeconds)) {
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

function Assert-PrometheusEndpoint {
    param(
        [string]$Url,
        [string]$Application
    )

    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
    if ($response.StatusCode -ne 200) {
        throw "Prometheus endpoint returned HTTP $($response.StatusCode): $Url"
    }

    $body = [string]$response.Content
    if (-not $body.Contains("jvm_memory_used_bytes")) {
        throw "JVM metrics were not found at $Url."
    }

    if (-not $body.Contains("application=`"$Application`"")) {
        throw "Application tag '$Application' was not found at $Url."
    }
}

function Wait-PrometheusFiveTargets {
    param([int]$TimeoutSeconds = 90)

    $queryUrl = "http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22nexapay-services%22%7D"
    $watch = [Diagnostics.Stopwatch]::StartNew()

    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $response = Invoke-RestMethod -Uri $queryUrl -TimeoutSec 5 -ErrorAction Stop
            if ($response.status -eq "success") {
                $rows = @($response.data.result)
                if ($rows.Count -eq 5) {
                    $down = @($rows | Where-Object { [string]$_.value[1] -ne "1" })
                    if ($down.Count -eq 0) {
                        return
                    }
                }
            }
        }
        catch {
        }

        Start-Sleep -Seconds 3
    }

    throw "Prometheus did not report all 5 NexaPay services as UP within $TimeoutSeconds seconds."
}

function Assert-GrafanaProvisioning {
    param([int]$TimeoutSeconds = 90)

    if (-not (Test-HttpHealth -Url "http://localhost:3000/api/health" -TimeoutSeconds $TimeoutSeconds)) {
        throw "Grafana health endpoint did not become available."
    }

    $pair = "admin:admin"
    $encoded = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    $headers = @{ Authorization = "Basic $encoded" }

    $datasource = Invoke-RestMethod `
        -Uri "http://localhost:3000/api/datasources/uid/prometheus" `
        -Headers $headers `
        -TimeoutSec 10 `
        -ErrorAction Stop

    if ($datasource.type -ne "prometheus") {
        throw "Grafana Prometheus datasource was not provisioned correctly."
    }

    $dashboard = Invoke-RestMethod `
        -Uri "http://localhost:3000/api/dashboards/uid/nexapay-overview" `
        -Headers $headers `
        -TimeoutSec 10 `
        -ErrorAction Stop

    if ($dashboard.dashboard.title -ne "NexaPay Overview") {
        throw "NexaPay Grafana dashboard was not provisioned correctly."
    }
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

    Invoke-Check "Full Maven reactor" {
        & mvn -B clean test
        if ($LASTEXITCODE -ne 0) {
            throw "mvn clean test returned exit code $LASTEXITCODE."
        }
    }

    if ($results["Full Maven reactor"] -ne $true) {
        throw "Full Maven reactor failed; runtime observability validation was skipped."
    }

    Write-Host "`n=== Starting NexaPay infrastructure + observability stack ==="
    & docker compose up -d postgres postgres-accounts postgres-ledger postgres-fraud postgres-auth kafka prometheus grafana
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed."
    }

    Wait-KafkaReady
    Wait-PostgresReady -Container "nexapay-postgres" -Database "nexapay_payments"
    Wait-PostgresReady -Container "nexapay-accounts-postgres" -Database "nexapay_accounts"
    Wait-PostgresReady -Container "nexapay-ledger-postgres" -Database "nexapay_ledger"
    Wait-PostgresReady -Container "nexapay-fraud-postgres" -Database "nexapay_fraud"
    Wait-PostgresReady -Container "nexapay-auth-postgres" -Database "nexapay_auth"

    $paymentProcess = Start-ServiceIfNeeded -Module "payment-service" -HealthUrl "http://localhost:8081/actuator/health" -Label "Payment"
    if ($null -ne $paymentProcess) { $ownedProcesses += $paymentProcess }

    $accountProcess = Start-ServiceIfNeeded -Module "account-service" -HealthUrl "http://localhost:8082/actuator/health" -Label "Account"
    if ($null -ne $accountProcess) { $ownedProcesses += $accountProcess }

    $ledgerProcess = Start-ServiceIfNeeded -Module "ledger-service" -HealthUrl "http://localhost:8083/actuator/health" -Label "Ledger"
    if ($null -ne $ledgerProcess) { $ownedProcesses += $ledgerProcess }

    $fraudProcess = Start-ServiceIfNeeded -Module "fraud-service" -HealthUrl "http://localhost:8084/actuator/health" -Label "Fraud"
    if ($null -ne $fraudProcess) { $ownedProcesses += $fraudProcess }

    $authProcess = Start-ServiceIfNeeded -Module "auth-service" -HealthUrl "http://localhost:8085/actuator/health" -Label "Auth"
    if ($null -ne $authProcess) { $ownedProcesses += $authProcess }

    Invoke-Check "Payment Prometheus endpoint" {
        Assert-PrometheusEndpoint `
            -Url "http://localhost:8081/actuator/prometheus" `
            -Application "nexapay-payment-service"
    }

    Invoke-Check "Account Prometheus endpoint" {
        Assert-PrometheusEndpoint `
            -Url "http://localhost:8082/actuator/prometheus" `
            -Application "nexapay-account-service"
    }

    Invoke-Check "Ledger Prometheus endpoint" {
        Assert-PrometheusEndpoint `
            -Url "http://localhost:8083/actuator/prometheus" `
            -Application "nexapay-ledger-service"
    }

    Invoke-Check "Fraud Prometheus endpoint" {
        Assert-PrometheusEndpoint `
            -Url "http://localhost:8084/actuator/prometheus" `
            -Application "nexapay-fraud-service"
    }

    Invoke-Check "Auth Prometheus endpoint" {
        Assert-PrometheusEndpoint `
            -Url "http://localhost:8085/actuator/prometheus" `
            -Application "nexapay-auth-service"
    }

    Invoke-Check "Prometheus scrapes 5 services" {
        Wait-PrometheusFiveTargets -TimeoutSeconds 90
    }

    Invoke-Check "Grafana datasource and dashboard" {
        Assert-GrafanaProvisioning -TimeoutSeconds 90
    }
}
catch {
    $fatalError = $_.Exception.Message
    Write-Host "`n[FATAL] $fatalError"
}
finally {
    foreach ($process in $ownedProcesses) {
        try {
            if (-not $process.HasExited) {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            }
        }
        catch {
        }
    }

    Pop-Location
}

Write-Host "`n========================================"
Write-Host "       NEXAPAY SPRINT 7 VALIDATION"
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
