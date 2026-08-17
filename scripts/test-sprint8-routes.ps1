$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

Push-Location $repoRoot
try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw "Required command not found: mvn"
    }

    Write-Host "=== Sprint 8 route definition validation ==="

    & mvn -B -pl gateway-service -Dtest=GatewayRouteDefinitionIntegrationTest test
    if ($LASTEXITCODE -ne 0) {
        throw "Gateway route definition integration test failed with exit code $LASTEXITCODE."
    }

    Write-Host "[PASS] Gateway loads 5 routes"
    exit 0
}
catch {
    Write-Host "[FAIL] Gateway loads 5 routes"
    Write-Host $_.Exception.Message
    exit 1
}
finally {
    Pop-Location
}
