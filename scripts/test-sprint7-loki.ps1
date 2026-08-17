param(
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$logDir = Join-Path $repoRoot "logs"
$runId = [guid]::NewGuid().ToString("N").Substring(0, 10)
$fileName = "sprint7-validation-$runId.log"
$validationLog = Join-Path $logDir $fileName
$containerLog = "/var/log/nexapay/$fileName"
$marker = "SPRINT7-LOKI-$runId"
$lastQueryError = $null

function Wait-Http {
    param(
        [string]$Url,
        [int]$Timeout
    )

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $Timeout) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
        }
        catch {
        }

        Start-Sleep -Seconds 2
    }

    throw "Endpoint did not become ready: $Url"
}

function Test-ValidationFileVisibleInAlloy {
    param([int]$Timeout = 20)

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $Timeout) {
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "SilentlyContinue"
            $output = & docker exec nexapay-alloy cat $containerLog 2>$null
            $exitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousPreference
        }

        if ($exitCode -eq 0 -and ([string]::Join("`n", @($output))).Contains($marker)) {
            return $true
        }

        Start-Sleep -Seconds 1
    }

    return $false
}

function Show-Diagnostics {
    Write-Host "`n--- Sprint 7 Loki diagnostics ---"
    Write-Host "Host file: $validationLog"
    Write-Host "Container file: $containerLog"

    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "SilentlyContinue"

        Write-Host "`n[Alloy files]"
        & docker exec nexapay-alloy sh -c "ls -la /var/log/nexapay 2>&1" 2>&1 | Select-Object -Last 40

        Write-Host "`n[Alloy logs]"
        & docker logs --tail 80 nexapay-alloy 2>&1 | Select-Object -Last 80

        Write-Host "`n[Loki logs]"
        & docker logs --tail 80 nexapay-loki 2>&1 | Select-Object -Last 80
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }

    if ($null -ne $lastQueryError) {
        Write-Host "`nLast Loki query error: $lastQueryError"
    }
}

Push-Location $repoRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Required command not found: docker"
    }

    New-Item -ItemType Directory -Force -Path $logDir | Out-Null

    & docker compose up -d loki alloy
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose up -d loki alloy failed."
    }

    & docker compose up -d --force-recreate alloy
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to recreate Alloy with the latest config."
    }

    Wait-Http -Url "http://localhost:3100/ready" -Timeout $TimeoutSeconds
    Wait-Http -Url "http://localhost:12345/" -Timeout $TimeoutSeconds

    # Create the validation file only after Alloy is healthy. This proves dynamic file discovery.
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText(
        $validationLog,
        "INFO $marker centralized-log-validation`n",
        $utf8NoBom
    )

    if (-not (Test-ValidationFileVisibleInAlloy -Timeout 20)) {
        throw "The validation log exists on Windows but is not visible inside the Alloy container bind mount."
    }

    Write-Host "[OK] Validation file is visible inside Alloy."

    $logQl = '{service_name="nexapay-sprint7-validation"} |= "' + $marker + '"'
    $encodedQuery = [Uri]::EscapeDataString($logQl)
    # Query only the recent window. Using start=0 can exceed Loki's max query length.
    $url = "http://localhost:3100/loki/api/v1/query_range?query=$encodedQuery&since=5m&limit=50&direction=backward"

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $response = Invoke-RestMethod -Uri $url -TimeoutSec 10 -ErrorAction Stop
            $lastQueryError = $null

            if ($response.status -eq "success") {
                foreach ($stream in @($response.data.result)) {
                    foreach ($value in @($stream.values)) {
                        if ([string]$value[1] -like "*$marker*") {
                            Write-Host "[PASS] Loki + Alloy centralized logs"
                            Write-Host "Marker: $marker"
                            exit 0
                        }
                    }
                }
            }
        }
        catch {
            $lastQueryError = $_.Exception.Message
        }

        Start-Sleep -Seconds 3
    }

    throw "Alloy did not deliver the isolated validation log line to Loki within $TimeoutSeconds seconds."
}
catch {
    Write-Host "[FAIL] Loki + Alloy centralized logs"
    Write-Host $_.Exception.Message
    Show-Diagnostics
    exit 1
}
finally {
    Pop-Location
}
