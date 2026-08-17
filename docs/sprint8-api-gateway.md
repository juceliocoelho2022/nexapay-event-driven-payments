# Sprint 8 — API Gateway

A Sprint 8 adiciona uma camada de entrada centralizada ao NexaPay usando Spring Cloud Gateway WebFlux.

## Objetivos concluídos

- Gateway HTTP central em `localhost:8080`;
- roteamento para Auth, Payment, Account, Ledger e Fraud;
- validação JWT HS256 no Gateway;
- autorização por `permissions` antes do encaminhamento ao microsserviço;
- rate limiting distribuído com Redis;
- métricas do Gateway via Actuator/Micrometer/Prometheus;
- logs do Gateway integrados ao pipeline Alloy/Loki;
- testes unitários, de integração e validação E2E automatizada.

## Fluxo

```text
Cliente
   |
   v
API Gateway :8080
   |
   +--> /api/v1/auth/**      -> Auth Service :8085
   +--> /api/v1/payments/**  -> Payment Service :8081
   +--> /api/v1/accounts/**  -> Account Service :8082
   +--> /api/v1/ledger/**    -> Ledger Service :8083
   +--> /api/v1/fraud/**     -> Fraud Service :8084

Gateway
   +--> JWT / permissions
   +--> Redis :6379 (rate limiting)
   +--> /actuator/prometheus
   +--> logs -> Alloy -> Loki
```

## Segurança no Gateway

As rotas públicas continuam limitadas a:

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
```

As rotas protegidas são validadas no Gateway usando o mesmo issuer e o mesmo segredo HS256 da arquitetura atual. A claim `permissions` do JWT é convertida diretamente em authorities, sem prefixo adicional.

Matriz central aplicada:

```text
GET  /api/v1/auth/me                         AUTH_SELF_READ
POST /api/v1/payments/pix                    PAYMENT_CREATE
GET  /api/v1/payments/**                     PAYMENT_READ
POST /api/v1/accounts/**                     ACCOUNT_WRITE
GET  /api/v1/accounts/**                     ACCOUNT_READ
GET  /api/v1/ledger/**                       LEDGER_READ
GET  /api/v1/fraud/**                        FRAUD_READ
```

Os microsserviços continuam mantendo suas próprias verificações de autorização. O Gateway adiciona uma primeira barreira central, mas não substitui a segurança interna dos serviços.

## Rate limiting

O `RequestRateLimiter` usa Redis e token bucket. A chave é baseada no usuário autenticado quando disponível; para chamadas anônimas, usa o endereço remoto.

Configuração atual:

```text
Auth       replenish 2/s   burst 4
Payment    replenish 20/s  burst 40
Account    replenish 20/s  burst 40
Ledger     replenish 20/s  burst 40
Fraud      replenish 10/s  burst 20
```

Quando o limite é excedido, o Gateway responde com HTTP `429 Too Many Requests`.

## Observabilidade

O Gateway expõe:

```http
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
GET /actuator/prometheus
```

O Prometheus possui um job dedicado `nexapay-gateway`, separado do job histórico dos cinco microsserviços. Os logs são gravados em `logs/nexapay-gateway-service.log` e coletados pelo Grafana Alloy para o Loki.

## Validação da Sprint 8

O validador principal é:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint8-gateway.ps1
```

Controles validados:

```text
Full Maven reactor                         PASS
Redis rate limiter ready                   PASS
Gateway health + Prometheus                PASS
Public auth routed through gateway         PASS
Gateway JWT authentication                 PASS
Gateway permission authorization           PASS
Payment route through gateway              PASS
Redis rate limiting -> 429                 PASS
Prometheus scrapes gateway                 PASS
```

A definição das rotas foi validada separadamente com:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint8-routes.ps1
```

Resultado:

```text
BUILD SUCCESS
[PASS] Gateway loads 5 routes
```

O teste de integração usa `RouteDefinitionLocator` e exige exatamente os IDs:

```text
auth-service
payment-service
account-service
ledger-service
fraud-service
```

Com os dois blocos de validação, a Sprint 8 possui 10/10 controles aprovados.

## Limitações mantidas

- JWT continua usando HS256 com segredo compartilhado no ambiente atual;
- não existe object-level authorization/ownership no Gateway;
- o Gateway não transforma a semântica Kafka em exactly-once;
- os microsserviços continuam acessíveis diretamente em suas portas locais durante o desenvolvimento;
- rate limiting atual usa Redis local e parâmetros voltados ao ambiente de estudo/portfólio;
- TLS, service discovery e deployment cloud permanecem fora desta sprint.

## Próxima etapa

Sprint 9 — Frontend React, consumindo o NexaPay prioritariamente pelo API Gateway.
