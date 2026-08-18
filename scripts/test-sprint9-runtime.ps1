param(
    [int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$frontendRoot = Join-Path $repoRoot "frontend"
$runId = [guid]::NewGuid().ToString("N").Substring(0, 10)
$ownedProcesses = @()
$fatalError = $null
$logDir = Join-Path $repoRoot "logs"
$oldLogDir = $env:NEXAPAY_LOG_DIR

$results = [ordered]@{
    "Frontend dev server + SPA routes" = $null
    "Frontend proxy reaches Gateway" = $null
    "Register/login through frontend" = $null
    "JWT auth/me through frontend" = $null
    "Account create/read through frontend" = $null
    "PIX create/read through frontend" = $null
    "Anonymous protection through frontend" = $null
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Invoke-NativeChecked {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$FailureMessage
    )

    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "SilentlyContinue"
        & $FilePath @Arguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }

    if ($exitCode -ne 0) {
        throw "$FailureMessage Exit code: $exitCode"
    }
}

function Test-HttpHealth {
    param([string]$Url, [int]$TimeoutSeconds)

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $true
            }
        }
        catch {}
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
        $response = Invoke-WebRequest -Uri $Url -Method $Method -Headers $Headers -UseBasicParsing -TimeoutSec 15 -ErrorAction Stop
        return [int]$response.StatusCode
    }
    catch {
        if ($null -ne $_.Exception.Response -and $null -ne $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

function Wait-PostgresReady {
    param([string]$Container, [string]$Database, [int]$TimeoutSeconds = 120)

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "SilentlyContinue"
            & docker exec $Container pg_isready -U nexapay -d $Database *> $null
            $exitCode = $LASTEXITCODE
        }
        finally { $ErrorActionPreference = $previousPreference }

        if ($exitCode -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    throw "$Container did not become ready within $TimeoutSeconds seconds."
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
        finally { $ErrorActionPreference = $previousPreference }

        if ($exitCode -eq 0) { return }
        Start-Sleep -Seconds 2
    }
    throw "Kafka did not become ready within $TimeoutSeconds seconds."
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
        finally { $ErrorActionPreference = $previousPreference }

        if ($exitCode -eq 0 -and ([string]$pong).Trim() -eq "PONG") { return }
        Start-Sleep -Seconds 2
    }
    throw "Redis did not become ready within $TimeoutSeconds seconds."
}

function Start-ServiceIfNeeded {
    param([string]$Module, [string]$HealthUrl, [string]$Label)

    if (Test-HttpHealth -Url $HealthUrl -TimeoutSeconds 4) {
        Write-Host "[$Label] already running; reusing current process."
        return $null
    }

    $mvnPath = (Get-Command mvn).Source
    $stdout = Join-Path $env:TEMP ("nexapay-{0}-sprint9-runtime-{1}.out.log" -f $Label.ToLower(), $runId)
    $stderr = Join-Path $env:TEMP ("nexapay-{0}-sprint9-runtime-{1}.err.log" -f $Label.ToLower(), $runId)

    $process = Start-Process -FilePath $mvnPath `
        -ArgumentList @("-B", "-pl", $Module, "spring-boot:run") `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

    if (-not (Test-HttpHealth -Url $HealthUrl -TimeoutSeconds $StartupTimeoutSeconds)) {
        Write-Host "--- $Label stdout ---"
        if (Test-Path $stdout) { Get-Content $stdout -Tail 100 }
        Write-Host "--- $Label stderr ---"
        if (Test-Path $stderr) { Get-Content $stderr -Tail 100 }
        try { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue } catch {}
        throw "$Label did not become healthy."
    }

    Write-Host "[$Label] started. PID=$($process.Id)"
    return $process
}

function Start-FrontendIfNeeded {
    if (Test-HttpHealth -Url "http://localhost:5173" -TimeoutSeconds 4) {
        Write-Host "[Frontend] already running; reusing current process."
        return $null
    }

    if ([string]::IsNullOrWhiteSpace($env:ComSpec)) {
        throw "Windows command processor (ComSpec) is unavailable."
    }

    $stdout = Join-Path $env:TEMP ("nexapay-frontend-sprint9-runtime-{0}.out.log" -f $runId)
    $stderr = Join-Path $env:TEMP ("nexapay-frontend-sprint9-runtime-{0}.err.log" -f $runId)

    $process = Start-Process -FilePath $env:ComSpec `
        -ArgumentList @("/d", "/s", "/c", "npm run dev -- --host 127.0.0.1") `
        -WorkingDirectory $frontendRoot `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

    if (-not (Test-HttpHealth -Url "http://localhost:5173" -TimeoutSeconds 60)) {
        Write-Host "--- Frontend stdout ---"
        if (Test-Path $stdout) { Get-Content $stdout -Tail 120 }
        Write-Host "--- Frontend stderr ---"
        if (Test-Path $stderr) { Get-Content $stderr -Tail 120 }
        try { & taskkill.exe /PID $process.Id /T /F *> $null } catch {}
        throw "Frontend did not become ready."
    }

    Write-Host "[Frontend] started through cmd.exe. PID=$($process.Id)"
    return $process
}

function Invoke-Check {
    param([string]$Name, [scriptblock]$Action)

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
    Require-Command "node"
    Require-Command "npm"

    if (-not (Test-Path (Join-Path $frontendRoot "node_modules"))) {
        throw "frontend/node_modules was not found. Run the full Sprint 9 validator once before this runtime-only validator."
    }

    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $env:NEXAPAY_LOG_DIR = $logDir

    Write-Host "=== Starting NexaPay runtime for Sprint 9 ==="
    Invoke-NativeChecked -FilePath (Get-Command docker).Source `
        -Arguments @("compose", "up", "-d", "postgres", "postgres-accounts", "postgres-auth", "kafka", "redis") `
        -FailureMessage "docker compose up failed."

    Wait-PostgresReady -Container "nexapay-postgres" -Database "nexapay_payments"
    Wait-PostgresReady -Container "nexapay-accounts-postgres" -Database "nexapay_accounts"
    Wait-PostgresReady -Container "nexapay-auth-postgres" -Database "nexapay_auth"
    Wait-KafkaReady
    Wait-RedisReady

    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "SilentlyContinue"
        & docker exec nexapay-redis redis-cli FLUSHDB *> $null
    }
    finally { $ErrorActionPreference = $previousPreference }

    foreach ($service in @(
        @{ Module = "auth-service"; Health = "http://localhost:8085/actuator/health"; Label = "Auth" },
        @{ Module = "account-service"; Health = "http://localhost:8082/actuator/health"; Label = "Account" },
        @{ Module = "payment-service"; Health = "http://localhost:8081/actuator/health"; Label = "Payment" },
        @{ Module = "gateway-service"; Health = "http://localhost:8080/actuator/health"; Label = "Gateway" }
    )) {
        $process = Start-ServiceIfNeeded -Module $service.Module -HealthUrl $service.Health -Label $service.Label
        if ($null -ne $process) { $ownedProcesses += @{ Process = $process; Tree = $false } }
    }

    $frontendProcess = Start-FrontendIfNeeded
    if ($null -ne $frontendProcess) { $ownedProcesses += @{ Process = $frontendProcess; Tree = $true } }

    Invoke-Check "Frontend dev server + SPA routes" {
        foreach ($path in @("/", "/accounts", "/payments", "/ledger", "/profile")) {
            $response = Invoke-WebRequest -Uri ("http://localhost:5173" + $path) -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
            if ($response.StatusCode -ne 200 -or -not ([string]$response.Content).Contains("NexaPay")) {
                throw "Frontend route '$path' was not served as the NexaPay SPA."
            }
        }
    }

    Invoke-Check "Frontend proxy reaches Gateway" {
        $health = Invoke-RestMethod -Uri "http://localhost:5173/actuator/health" -TimeoutSec 10 -ErrorAction Stop
        if ($health.status -ne "UP") { throw "Gateway health through Vite proxy is not UP." }
    }

    $email = "sprint9-$runId@nexapay.test"
    $password = "NexaPay!2026"
    $registerBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $loginBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $token = $null

    Invoke-Check "Register/login through frontend" {
        $register = Invoke-WebRequest `
            -Uri "http://localhost:5173/api/v1/auth/register" `
            -Method POST `
            -ContentType "application/json" `
            -Body $registerBody `
            -UseBasicParsing `
            -TimeoutSec 15 `
            -ErrorAction Stop
        if ([int]$register.StatusCode -ne 201) { throw "Registration returned HTTP $($register.StatusCode)." }

        $login = Invoke-RestMethod `
            -Uri "http://localhost:5173/api/v1/auth/login" `
            -Method POST `
            -ContentType "application/json" `
            -Body $loginBody `
            -TimeoutSec 15 `
            -ErrorAction Stop

        $script:token = [string]$login.accessToken
        if ([string]::IsNullOrWhiteSpace($script:token)) { throw "Login did not return accessToken." }
    }

    if ($results["Register/login through frontend"] -ne $true) {
        throw "Auth flow failed; protected runtime checks were skipped."
    }

    $authHeaders = @{ Authorization = "Bearer $token" }

    Invoke-Check "JWT auth/me through frontend" {
        $me = Invoke-RestMethod -Uri "http://localhost:5173/api/v1/auth/me" -Headers $authHeaders -TimeoutSec 10 -ErrorAction Stop
        if ($me.email -ne $email) { throw "auth/me returned an unexpected user." }
        if (-not (@($me.permissions) -contains "ACCOUNT_WRITE")) { throw "Expected ACCOUNT_WRITE permission was not returned." }
    }

    $accountId = $null
    Invoke-Check "Account create/read through frontend" {
        $createBody = @{
            accountNumber = "S9-$runId"
            holderName = "Sprint 9 Frontend"
        } | ConvertTo-Json -Compress

        $created = Invoke-RestMethod `
            -Uri "http://localhost:5173/api/v1/accounts" `
            -Method POST `
            -Headers $authHeaders `
            -ContentType "application/json" `
            -Body $createBody `
            -TimeoutSec 15 `
            -ErrorAction Stop

        $script:accountId = [string]$created.id
        if ([string]::IsNullOrWhiteSpace($script:accountId)) { throw "Account creation did not return id." }

        $loaded = Invoke-RestMethod -Uri ("http://localhost:5173/api/v1/accounts/{0}" -f $script:accountId) -Headers $authHeaders -TimeoutSec 10 -ErrorAction Stop
        if ([string]$loaded.id -ne $script:accountId) { throw "Account read returned an unexpected id." }
    }

    Invoke-Check "PIX create/read through frontend" {
        if ([string]::IsNullOrWhiteSpace([string]$accountId)) { throw "Account id is unavailable." }

        $paymentBody = @{
            payerAccountId = [string]$accountId
            pixKey = "sprint9-$runId@pix.test"
            amount = 123.45
            description = "Sprint 9 frontend runtime validation"
        } | ConvertTo-Json -Compress

        $headers = @{
            Authorization = "Bearer $token"
            "Idempotency-Key" = "sprint9-runtime-$runId"
        }

        $created = Invoke-RestMethod `
            -Uri "http://localhost:5173/api/v1/payments/pix" `
            -Method POST `
            -Headers $headers `
            -ContentType "application/json" `
            -Body $paymentBody `
            -TimeoutSec 15 `
            -ErrorAction Stop

        $paymentId = [string]$created.id
        if ([string]::IsNullOrWhiteSpace($paymentId)) { throw "PIX creation did not return id." }

        $loaded = Invoke-RestMethod -Uri ("http://localhost:5173/api/v1/payments/{0}" -f $paymentId) -Headers $authHeaders -TimeoutSec 10 -ErrorAction Stop
        if ([string]$loaded.id -ne $paymentId) { throw "PIX read returned an unexpected id." }
    }

    Invoke-Check "Anonymous protection through frontend" {
        $status = Invoke-HttpStatus -Url ("http://localhost:5173/api/v1/payments/{0}" -f [guid]::NewGuid())
        if ($status -ne 401) { throw "Anonymous protected request returned HTTP $status, expected 401." }
    }
}
catch {
    $fatalError = $_.Exception.Message
    Write-Host "`n[FATAL] $fatalError"
}
finally {
    foreach ($owned in $ownedProcesses) {
        try {
            $process = $owned.Process
            if ($owned.Tree) {
                $previousPreference = $ErrorActionPreference
                try {
                    $ErrorActionPreference = "SilentlyContinue"
                    & taskkill.exe /PID $process.Id /T /F *> $null
                }
                finally { $ErrorActionPreference = $previousPreference }
            }
            else {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            }
        }
        catch {}
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
Write-Host "   NEXAPAY SPRINT 9 RUNTIME VALIDATION"
Write-Host "========================================"
foreach ($entry in $results.GetEnumerator()) {
    $status = if ($entry.Value -eq $true) { "PASS" } elseif ($entry.Value -eq $false) { "FAIL" } else { "SKIP" }
    Write-Host ("{0,-43} {1}" -f $entry.Key, $status)
}

$failed = @($results.GetEnumerator() | Where-Object { $_.Value -ne $true })
if ($null -ne $fatalError -or $failed.Count -gt 0) { exit 1 }
exit 0
