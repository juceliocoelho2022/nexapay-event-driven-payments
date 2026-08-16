param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("ledger-credit", "ledger-debit", "fraud-payment")]
    [string]$Route,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Marker,

    [switch]$Replay,

    [int]$ReadTimeoutMs = 10000
)

$ErrorActionPreference = "Stop"

$routes = @{
    "ledger-credit" = "nexapay.account.credited.v1"
    "ledger-debit" = "nexapay.account.debited.v1"
    "fraud-payment" = "nexapay.payment.created.v1"
}

$originalTopic = $routes[$Route]
$dltTopic = "$originalTopic.DLT"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker command was not found."
}

Write-Host "NexaPay controlled DLT replay"
Write-Host "Route:          $Route"
Write-Host "DLT topic:      $dltTopic"
Write-Host "Original topic: $originalTopic"
Write-Host "Marker:         $Marker"

$output = & docker exec nexapay-kafka `
    /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server localhost:9092 `
    --topic $dltTopic `
    --from-beginning `
    --timeout-ms $ReadTimeoutMs 2>$null

$matches = @($output | Where-Object { $_ -like "*$Marker*" })

if ($matches.Count -eq 0) {
    throw "No DLT record matched marker '$Marker' in $dltTopic."
}

if ($matches.Count -gt 1) {
    throw "Marker '$Marker' matched $($matches.Count) DLT records. Use a unique eventId or unique business marker before replaying."
}

$payload = [string]$matches[0]

Write-Host "`nSelected DLT record:"
Write-Host $payload

if (-not $Replay) {
    Write-Host "`nDRY RUN only. Nothing was republished."
    Write-Host "Run the same command with -Replay after verifying the selected record."
    exit 0
}

$payload | & docker exec -i nexapay-kafka `
    /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server localhost:9092 `
    --topic $originalTopic

if ($LASTEXITCODE -ne 0) {
    throw "Kafka producer failed while replaying the selected DLT record."
}

Write-Host "`nReplay published successfully to $originalTopic."
Write-Host "The original DLT record remains in Kafka as an immutable audit/quarantine record."
Write-Host "Consumer-side idempotency must decide whether the replay creates new state or is ignored as a duplicate."
