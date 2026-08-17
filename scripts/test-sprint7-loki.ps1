param(
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$logDir = Join-Path $repoRoot "logs"
$validationLog = Join-Path $logDir "sprint7-validation.log"
$runId = [guid]::NewGuid().ToString("N").Substring(0, 10)
$marker = "SPRINT7-LOKI-$runId"

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

Push-Location $repoRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Required command not found: docker"
    }

    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    Set-Content -Path $validationLog -Value "INFO $marker centralized-log-validation" -Encoding UTF8

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

    $logQl = '{service_name="nexapay-sprint7-validation"} |= "' + $marker + '"'
    $encodedQuery = [Uri]::EscapeDataString($logQl)
    $url = "http://localhost:3100/loki/api/v1/query_range?query=$encodedQuery&start=0&limit=50&direction=backward"

    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        try {
            $response = Invoke-RestMethod -Uri $url -TimeoutSec 10 -ErrorAction Stop
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
        }

        Start-Sleep -Seconds 3
    }

    throw "Alloy did not deliver the isolated validation log line to Loki within $TimeoutSeconds seconds."
}
catch {
    Write-Host "[FAIL] Loki + Alloy centralized logs"
    Write-Host $_.Exception.Message
    exit 1
}
finally {
    Pop-Location
}
