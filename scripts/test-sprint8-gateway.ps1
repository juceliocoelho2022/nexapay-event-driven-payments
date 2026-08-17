param(
    [int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runId = [guid]::NewGuid().ToString("N").Substring(0, 10)
$ownedProcesses = @()
$fatalError = $null
$oldLogDir = $env:NEXAPAY_LOG_DIR
$logDir = Join-Path $repoRoot "logs"

$results = [ordered]@{
    "Full Maven reactor" = $null
    "Redis rate limiter ready" = $null
    "Gateway health + Prometheus" = $null
    "Gateway loads 5 routes" = $null
    "Public auth routed through gateway" = $null
    "Gateway JWT authentication" = $null
    "Gateway permission authorization" = $null
    "Payment route through gateway" = $null
    "Redis rate limiting -> 429" = $null
    "Prometheus scrapes gateway" = $null
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

function Invoke-HttpStatus {
    param(
        [string]$Url,
        [string]$Method = "GET",
        [hashtable]$Headers = @{}
    )

    try {
        $response = Invoke-WebRequest `
            -Uri $Url `
            -Method $Method `
            -Headers $Headers `
            -UseBasicParsing `
            -TimeoutSec 10 `
            -ErrorAction Stop
        return [int]$response.StatusCode
    }
    catch {
        if ($null -ne $_.Exception.Response -and $null -ne $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
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

function Wait-RedisReady {
    param([int]$TimeoutSeconds = 60)

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "SilentlyContinue"
            $pong = & docker exec nexapay-redis redis-cli ping 2>$null
            $exitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousPreference
        }

        if ($exitCode -eq 0 -and ([string]$pong).Trim() -eq "PONG") {
            return
        }

        Start-Sleep -Seconds 2
    }

    throw "Redis did not become ready within $TimeoutSeconds seconds."
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
    $stdout = Join-Path $env:TEMP ("nexapay-{0}-sprint8-{1}.out.log" -f $Label.ToLower(), $runId)
    $stderr = Join-Path $env:TEMP ("nexapay-{0}-sprint8-{1}.err.log" -f $Label.ToLower(), $runId)

    $process = Start-Process -FilePath $mvnPath `
        -ArgumentList @("-B", "-pl", $Module, "spring-boot:run") `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

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

function Wait-PrometheusGatewayTarget {
    param([int]$TimeoutSeconds = 90)

    $queryUrl = "http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22nexapay-gateway%22%7D"
    $watch = [Diagnostics.Stopwatch]::StartNew()

    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $response = Invoke-RestMethod -Uri $queryUrl -TimeoutSec 5 -ErrorAction Stop
            if ($response.status -eq "success") {
                $rows = @($response.data.result)
                if ($rows.Count -eq 1 -and [string]$rows[0].value[1] -eq "1") {
                    return
                }
            }
        }
        catch {
        }

        Start-Sleep -Seconds 3
    }

    throw "Prometheus did not report NexaPay Gateway as UP within $TimeoutSeconds seconds."
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

    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $env:NEXAPAY_LOG_DIR = $logDir

    Invoke-Check "Full Maven reactor" {
        & mvn -B clean test
        if ($LASTEXITCODE -ne 0) {
            throw "mvn clean test returned exit code $LASTEXITCODE."
        }
    }

    if ($results["Full Maven reactor"] -ne $true) {
        throw "Full Maven reactor failed; runtime gateway validation was skipped."
    }

    Write-Host "`n=== Starting NexaPay infrastructure for Sprint 8 ==="
    & docker compose up -d postgres postgres-auth kafka redis prometheus
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up failed."
    }

    & docker compose up -d --force-recreate prometheus
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to recreate Prometheus with the Sprint 8 config."
    }

    Wait-PostgresReady -Container "nexapay-postgres" -Database "nexapay_payments"
    Wait-PostgresReady -Container "nexapay-auth-postgres" -Database "nexapay_auth"
    Wait-KafkaReady

    Invoke-Check "Redis rate limiter ready" {
        Wait-RedisReady -TimeoutSeconds 60
        & docker exec nexapay-redis redis-cli FLUSHDB *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Could not reset Redis rate limiter state."
        }
    }

    $authProcess = Start-ServiceIfNeeded -Module "auth-service" -HealthUrl "http://localhost:8085/actuator/health" -Label "Auth"
    if ($null -ne $authProcess) { $ownedProcesses += $authProcess }

    $paymentProcess = Start-ServiceIfNeeded -Module "payment-service" -HealthUrl "http://localhost:8081/actuator/health" -Label "Payment"
    if ($null -ne $paymentProcess) { $ownedProcesses += $paymentProcess }

    $gatewayProcess = Start-ServiceIfNeeded -Module "gateway-service" -HealthUrl "http://localhost:8080/actuator/health" -Label "Gateway"
    if ($null -ne $gatewayProcess) { $ownedProcesses += $gatewayProcess }

    Invoke-Check "Gateway health + Prometheus" {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 10 -ErrorAction Stop
        if ($health.status -ne "UP") {
            throw "Gateway health status is not UP."
        }

        $metrics = [string](Invoke-WebRequest `
            -Uri "http://localhost:8080/actuator/prometheus" `
            -UseBasicParsing `
            -TimeoutSec 10 `
            -ErrorAction Stop).Content

        if (-not $metrics.Contains("application=`"nexapay-gateway-service`"")) {
            throw "Gateway application tag was not found in Prometheus metrics."
        }
    }

    Invoke-Check "Gateway loads 5 routes" {
        $metric = Invoke-RestMethod `
            -Uri "http://localhost:8080/actuator/metrics/spring.cloud.gateway.routes.count" `
            -TimeoutSec 10 `
            -ErrorAction Stop

        $values = @($metric.measurements | ForEach-Object { [double]$_.value })
        if ($values.Count -eq 0 -or $values[0] -ne 5) {
            throw "Expected 5 gateway routes, got: $($values -join ', ')"
        }
    }

    $email = "sprint8-$runId@nexapay.test"
    $password = "NexaPay!2026"
    $registerBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $loginBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $token = $null

    Invoke-Check "Public auth routed through gateway" {
        $register = Invoke-WebRequest `
            -Uri "http://localhost:8080/api/v1/auth/register" `
            -Method POST `
            -ContentType "application/json" `
            -Body $registerBody `
            -UseBasicParsing `
            -TimeoutSec 15 `
            -ErrorAction Stop

        if ([int]$register.StatusCode -ne 201) {
            throw "Gateway registration returned HTTP $($register.StatusCode), expected 201."
        }

        $login = Invoke-RestMethod `
            -Uri "http://localhost:8080/api/v1/auth/login" `
            -Method POST `
            -ContentType "application/json" `
            -Body $loginBody `
            -TimeoutSec 15 `
            -ErrorAction Stop

        $script:token = [string]$login.accessToken
        if ([string]::IsNullOrWhiteSpace($script:token)) {
            throw "Gateway login did not return accessToken."
        }
    }

    if ($results["Public auth routed through gateway"] -ne $true) {
        throw "Gateway auth flow failed; authenticated checks were skipped."
    }

    $authHeaders = @{ Authorization = "Bearer $token" }

    Invoke-Check "Gateway JWT authentication" {
        $me = Invoke-RestMethod `
            -Uri "http://localhost:8080/api/v1/auth/me" `
            -Headers $authHeaders `
            -TimeoutSec 10 `
            -ErrorAction Stop

        if ($me.email -ne $email) {
            throw "Gateway /auth/me returned an unexpected user."
        }

        $anonymousStatus = Invoke-HttpStatus -Url ("http://localhost:8080/api/v1/payments/{0}" -f [guid]::NewGuid())
        if ($anonymousStatus -ne 401) {
            throw "Anonymous protected request returned HTTP $anonymousStatus, expected 401."
        }
    }

    Invoke-Check "Gateway permission authorization" {
        $status = Invoke-HttpStatus `
            -Url ("http://localhost:8080/api/v1/fraud/payments/{0}" -f [guid]::NewGuid()) `
            -Headers $authHeaders

        if ($status -ne 403) {
            throw "ROLE_USER fraud request returned HTTP $status, expected 403 at the gateway."
        }
    }

    Invoke-Check "Payment route through gateway" {
        $status = Invoke-HttpStatus `
            -Url ("http://localhost:8080/api/v1/payments/{0}" -f [guid]::NewGuid()) `
            -Headers $authHeaders

        if ($status -ne 404) {
            throw "Gateway payment route returned HTTP $status, expected downstream 404 for a random payment id."
        }
    }

    Invoke-Check "Redis rate limiting -> 429" {
        & docker exec nexapay-redis redis-cli FLUSHDB *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Could not reset Redis before rate-limit validation."
        }

        $statuses = @()
        for ($i = 0; $i -lt 20; $i++) {
            $statuses += Invoke-HttpStatus `
                -Url "http://localhost:8080/api/v1/auth/me" `
                -Headers $authHeaders
        }

        if (-not ($statuses -contains 429)) {
            throw "No HTTP 429 was observed. Statuses: $($statuses -join ', ')"
        }
    }

    Invoke-Check "Prometheus scrapes gateway" {
        if (-not (Test-HttpHealth -Url "http://localhost:9090/-/ready" -TimeoutSeconds 60)) {
            throw "Prometheus did not become ready."
        }
        Wait-PrometheusGatewayTarget -TimeoutSeconds 90
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

    if ($null -eq $oldLogDir) {
        Remove-Item Env:NEXAPAY_LOG_DIR -ErrorAction SilentlyContinue
    }
    else {
        $env:NEXAPAY_LOG_DIR = $oldLogDir
    }

    Pop-Location
}

Write-Host "`n========================================"
Write-Host "       NEXAPAY SPRINT 8 VALIDATION"
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
