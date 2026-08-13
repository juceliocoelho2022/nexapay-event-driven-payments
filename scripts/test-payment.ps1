$headers = @{
    "Idempotency-Key" = "pedido-001"
    "Content-Type" = "application/json"
}

$body = @{
    payerAccountId = "ACC-1001"
    pixKey = "cliente@email.com"
    amount = 250.00
    description = "Pagamento de teste NexaPay"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8081/api/v1/payments/pix" `
    -Headers $headers `
    -Body $body
