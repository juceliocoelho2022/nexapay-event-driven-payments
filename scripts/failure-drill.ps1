param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("kafka", "fraud-db", "fraud-service")]
    [string]$Scenario,

    [string]$ComposeFile = "docker-compose.yml",

    [int]$OutageSeconds = 20,

    [string]$PrometheusUrl = "http://localhost:9090",

    [string]$EvidenceDirectory = "docs/evidence"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$composePath = Join-Path $repoRoot $ComposeFile

if (-not (Test-Path $composePath)) {
    throw "Compose file not found: $composePath"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker command was not found."
}

$service = switch ($Scenario) {
    "kafka" { "kafka" }
    "fraud-db" { "postgres-fraud" }
    "fraud-service" { "fraud-service" }
}

if ($Scenario -eq "fraud-service" -and $ComposeFile -eq "docker-compose.yml") {
    throw "fraud-service is not containerized in docker-compose.yml. Use the prod-like compose file for this drill."
}

$evidencePath = Join-Path $repoRoot $EvidenceDirectory
New-Item -ItemType Directory -Force -Path $evidencePath | Out-Null

$runId = "{0}-{1}" -f $Scenario, (Get-Date -Format "yyyyMMdd-HHmmss")
$outputFile = Join-Path $evidencePath "$runId.json"

function Invoke-PrometheusQuery {
    param([string]$Query)

    try {
        $encoded = [Uri]::EscapeDataString($Query)
        $response = Invoke-RestMethod -Method Get -Uri "$PrometheusUrl/api/v1/query?query=$encoded" -TimeoutSec 5
        return $response.data.result
    }
    catch {
        return @(@{
            error = $_.Exception.Message
        })
    }
}

function Capture-Snapshot {
    param([string]$Phase)

    return [ordered]@{
        phase = $Phase
        capturedAt = [DateTimeOffset]::UtcNow.ToString("o")
        availability = Invoke-PrometheusQuery 'avg(up{job=~"nexapay-services|nexapay-gateway"})'
        outboxPending = Invoke-PrometheusQuery 'sum(nexapay_outbox_batch_pending)'
        outboxPublishFailures10m = Invoke-PrometheusQuery 'sum(increase(nexapay_outbox_publish_failures_total[10m]))'
        kafkaRetries10m = Invoke-PrometheusQuery 'sum(increase(nexapay_kafka_retry_attempts_total[10m]))'
        dltPublished10m = Invoke-PrometheusQuery 'sum(increase(nexapay_kafka_dlt_published_total[10m]))'
    }
}

Push-Location $repoRoot
$startedRecovery = $false
try {
    & docker compose -f $ComposeFile config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose config failed for $ComposeFile."
    }

    Write-Host "NexaPay controlled failure drill"
    Write-Host "Scenario:       $Scenario"
    Write-Host "Compose file:   $ComposeFile"
    Write-Host "Target service: $service"
    Write-Host "Outage:         $OutageSeconds seconds"
    Write-Host ""
    Write-Host "IMPORTANT: generate normal application traffic during the outage if the scenario requires business-flow evidence."

    $before = Capture-Snapshot -Phase "before"

    & docker compose -f $ComposeFile stop $service
    if ($LASTEXITCODE -ne 0) {
        throw "Could not stop compose service '$service'."
    }

    Write-Host "Failure injected. Waiting $OutageSeconds seconds..."
    Start-Sleep -Seconds $OutageSeconds

    $during = Capture-Snapshot -Phase "during"

    & docker compose -f $ComposeFile start $service
    if ($LASTEXITCODE -ne 0) {
        throw "Could not start compose service '$service'."
    }
    $startedRecovery = $true

    Write-Host "Recovery started. Waiting 15 seconds before post-recovery snapshot..."
    Start-Sleep -Seconds 15

    $after = Capture-Snapshot -Phase "after"

    $evidence = [ordered]@{
        runId = $runId
        scenario = $Scenario
        composeFile = $ComposeFile
        targetService = $service
        outageSeconds = $OutageSeconds
        note = "Metric snapshots are evidence helpers; correlate them with Grafana, Tempo, Loki and business-state validation."
        snapshots = @($before, $during, $after)
    }

    $evidence | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 $outputFile

    Write-Host ""
    Write-Host "Failure drill completed."
    Write-Host "Evidence: $outputFile"
    Write-Host "Now validate the business recovery criteria in docs/production-hardening.md."
}
finally {
    if (-not $startedRecovery) {
        Write-Host "Ensuring target service is started before exit..."
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "SilentlyContinue"
            & docker compose -f $ComposeFile start $service *> $null
        }
        finally {
            $ErrorActionPreference = $previousPreference
        }
    }

    Pop-Location
}
