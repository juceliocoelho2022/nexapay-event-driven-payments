# NexaPay

<p align="center">
  <img src="docs/images/nexapay-logo.png" alt="NexaPay Logo" width="500"/>
</p>

<p align="center">
  <strong>Event-Driven Payment Platform</strong>
</p>

<p align="center">
  Plataforma backend de pagamentos construída com Java 21, Spring Boot, Apache Kafka, PostgreSQL, Flyway, Docker Compose, Transactional Outbox, Spring Security e observabilidade com Prometheus, Grafana, Loki e Alloy.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen" alt="Spring Boot 3.5.16"/>
  <img src="https://img.shields.io/badge/PostgreSQL-17-blue" alt="PostgreSQL 17"/>
  <img src="https://img.shields.io/badge/Apache%20Kafka-3.9.x-black" alt="Apache Kafka"/>
  <img src="https://img.shields.io/badge/Security-JWT-blueviolet" alt="JWT Security"/>
  <img src="https://img.shields.io/badge/Observability-Prometheus%20%7C%20Grafana%20%7C%20Loki-orange" alt="Observability"/>
  <img src="https://img.shields.io/badge/Sprints-7%20conclu%C3%ADdas-success" alt="7 Sprints concluídas"/>
</p>

---

## Sobre o projeto

O **NexaPay** é um projeto de portfólio de engenharia de software backend Java voltado a sistemas financeiros distribuídos e orientados a eventos.

A implementação atual possui cinco microsserviços Maven independentes:

- `payment-service` — criação e consulta de pagamentos PIX;
- `account-service` — contas, saldo, crédito e débito;
- `ledger-service` — histórico financeiro consumido via Kafka;
- `fraud-service` — análise assíncrona de risco;
- `auth-service` — cadastro, autenticação, emissão de JWT e autorização por roles/permissions.

### Status

```text
Sprint 1 — Payment Service   ✅ Concluída
Sprint 2 — Account Service   ✅ Concluída
Sprint 3 — Ledger Service    ✅ Concluída
Sprint 4 — Fraud Service     ✅ Concluída
Sprint 5 — Segurança         ✅ Concluída
Sprint 6 — Resiliência       ✅ Concluída
Sprint 7 — Observabilidade   ✅ Concluída
Sprint 8 — API Gateway       ⏳ Próxima
```

---

## Arquitetura implementada

```text
                              ┌────────────────────┐
                              │      Cliente       │
                              └─────────┬──────────┘
                                        │ login / JWT
                                        v
                              ┌────────────────────┐
                              │    Auth Service    │
                              │       :8085        │
                              └─────────┬──────────┘
                                        │ Bearer JWT
                    ┌───────────────────┼───────────────────┐
                    │                   │                   │
                    v                   v                   v
          ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
          │ Payment :8081  │  │ Account :8082  │  │ Ledger :8083   │
          └───────┬────────┘  └───────┬────────┘  └───────┬────────┘
                  │                   │                   │
        PostgreSQL :5435    PostgreSQL :5436    PostgreSQL :5437
                  │                   │                   ▲
                  │ Outbox            │ Outbox            │
                  v                   v                   │
                Kafka               Kafka                │
                  │          ┌────────┴────────┐          │
                  │          │                 │          │
                  │          v                 v          │
                  │   account.credited  account.debited ─┘
                  │          │                 │
                  │          └──── retry / DLT ┘
                  v
          payment.created
                  │
                  │ retry / DLT
                  v
          ┌────────────────┐
          │  Fraud :8084   │
          └───────┬────────┘
                  │
        PostgreSQL :5438

          Auth PostgreSQL :5439

Observabilidade:
  Serviços /actuator/prometheus -> Prometheus :9090 -> Grafana :3000
  logs/*.log -> Grafana Alloy :12345 -> Loki :3100 -> Grafana :3000
```

> O arquivo `docs/images/nexapay-architecture.png` representa a evolução visual do projeto. O diagrama textual acima descreve a arquitetura implementada até a Sprint 7.

### Semântica de eventos

Os produtores usam **Transactional Outbox** para persistir alteração de domínio e registro de evento na mesma transação local.

A publicação e o consumo Kafka possuem semântica **at-least-once**. O envio ao Kafka e a marcação do Outbox como publicado não formam uma única transação distribuída, e a recuperação via DLT também não é uma transação global com o commit do offset.

Por isso, a arquitetura assume que consumidores devem ser idempotentes e tolerar reprocessamento. O projeto **não reivindica exactly-once**.

---

## Stack

### Backend

- Java 21
- Spring Boot 3.5.16
- Spring Web
- Spring Data JPA / Hibernate
- Bean Validation
- Spring Security
- OAuth2 Resource Server JWT
- Maven multi-module

### Dados e mensageria

- PostgreSQL 17
- Flyway
- Apache Kafka 3.9.x
- Transactional Outbox
- Retry com Spring Kafka
- Dead Letter Topics
- bancos dedicados por serviço

### Observabilidade

- Spring Boot Actuator
- Micrometer
- Prometheus
- Grafana
- Loki
- Grafana Alloy
- dashboards provisionados automaticamente
- métricas JVM/HTTP e métricas de domínio/resiliência do NexaPay
- logs centralizados dos microsserviços

### Testes

- JUnit 5
- Mockito
- Spring Boot Test
- Spring Security Test
- MockMvc
- Testcontainers
- testes de concorrência com PostgreSQL real
- testes E2E de retry, DLT e replay controlado
- validação automatizada de Prometheus, Grafana, Loki e Alloy

### Infraestrutura

- Docker
- Docker Compose
- Spring Boot Actuator

---

# Serviços

## Payment Service — Sprint 1 + hardening Sprint 6/7 ✅

**Aplicação:** `8081`  
**PostgreSQL:** `5435`  
**Banco:** `nexapay_payments`

```http
POST /api/v1/payments/pix
GET  /api/v1/payments/{id}
```

A criação exige `Idempotency-Key` e autorização `PAYMENT_CREATE`. A consulta exige `PAYMENT_READ`.

O serviço grava pagamento + Outbox Event na mesma operação de negócio e publica:

```text
nexapay.payment.created.v1
```

Na Sprint 6, a idempotência foi fortalecida para concorrência. A criação usa reserva atômica no PostgreSQL com `INSERT ... ON CONFLICT DO NOTHING`, apoiada pela constraint única de `idempotency_key`. Chamadas simultâneas com a mesma chave convergem para um único pagamento persistido, comportamento coberto por teste concorrente com Testcontainers.

Na Sprint 7, o serviço passou a expor métricas Prometheus e métricas do Outbox, incluindo lote pendente, publicações e falhas de publicação.

---

## Account Service — Sprint 2 + observabilidade Sprint 7 ✅

**Aplicação:** `8082`  
**PostgreSQL:** `5436`  
**Banco:** `nexapay_accounts`

```http
POST /api/v1/accounts
GET  /api/v1/accounts/{id}
POST /api/v1/accounts/{id}/credit
POST /api/v1/accounts/{id}/debit
```

Permissões:

```text
GET                         ACCOUNT_READ
POST /accounts              ACCOUNT_WRITE
POST /credit                ACCOUNT_WRITE
POST /debit                 ACCOUNT_WRITE
```

Recursos principais:

- `BigDecimal` para valores monetários;
- transações Spring;
- `PESSIMISTIC_WRITE` para operações de saldo;
- Transactional Outbox;
- publicação de `nexapay.account.credited.v1` e `nexapay.account.debited.v1`;
- métricas Prometheus e métricas do Outbox.

---

## Ledger Service — Sprint 3 + hardening Sprint 6/7 ✅

**Aplicação:** `8083`  
**PostgreSQL:** `5437`  
**Banco:** `nexapay_ledger`  
**Kafka group:** `nexapay-ledger-service`

Consome:

```text
nexapay.account.credited.v1
nexapay.account.debited.v1
```

DLTs:

```text
nexapay.account.credited.v1.DLT
nexapay.account.debited.v1.DLT
```

Endpoint protegido:

```http
GET /api/v1/ledger/accounts/{accountId}   -> LEDGER_READ
```

Recursos de resiliência:

- `DefaultErrorHandler`;
- `FixedBackOff` de 1 segundo;
- política configurada com 2 retries após a tentativa inicial;
- `DeadLetterPublishingRecoverer`;
- DLT por tópico original, preservando a partição;
- payload JSON inválido tratado como não-retryable e encaminhado diretamente para recuperação/DLT;
- proteção contra replay concorrente por `eventId` com operação atômica no PostgreSQL;
- validação E2E com indisponibilidade real do PostgreSQL;
- métricas de falhas de entrega, retries, publicação em DLT e falhas de recuperação.

> Não é um ledger contábil double-entry; registra lançamentos derivados dos eventos do Account Service.

---

## Fraud Service — Sprint 4 + hardening Sprint 6/7 ✅

**Aplicação:** `8084`  
**PostgreSQL:** `5438`  
**Banco:** `nexapay_fraud`  
**Kafka group:** `nexapay-fraud-service`

Consome:

```text
nexapay.payment.created.v1
```

DLT:

```text
nexapay.payment.created.v1.DLT
```

Motor de regras atual:

```text
Valor < R$ 5.000                 -> APPROVED | score 20
R$ 5.000 <= valor < R$ 10.000   -> REVIEW   | score 70
Valor >= R$ 10.000              -> BLOCKED  | score 95
```

Endpoint protegido:

```http
GET /api/v1/fraud/payments/{paymentId}   -> FRAUD_READ
```

Recursos de resiliência e observabilidade:

- mesma política de retry/DLT do Ledger;
- payload inválido classificado como não-retryable;
- replay concorrente protegido por `eventId` no PostgreSQL;
- teste concorrente de idempotência;
- teste E2E de falha do PostgreSQL seguido de DLT;
- replay controlado após recuperação do banco;
- métricas Kafka de falha/retry/DLT;
- métricas das decisões de fraude.

`ROLE_USER` não recebe `FRAUD_READ`; a permissão fica reservada ao `ROLE_ADMIN` no modelo atual.

> A decisão de fraude ainda não altera automaticamente o status do Payment Service.

---

## Auth Service — Sprint 5 + observabilidade Sprint 7 ✅

**Aplicação:** `8085`  
**PostgreSQL:** `5439`  
**Banco:** `nexapay_auth`

Endpoints públicos:

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /actuator/health
GET  /actuator/info
GET  /actuator/prometheus
```

Endpoint autenticado:

```http
GET /api/v1/auth/me   -> AUTH_SELF_READ
```

### Recursos implementados

- cadastro de usuário;
- normalização de e-mail;
- senha armazenada com `DelegatingPasswordEncoder` / bcrypt;
- roles `ROLE_USER` e `ROLE_ADMIN`;
- permissions persistidas em PostgreSQL;
- emissão de JWT com `sub`, `email`, `roles` e `permissions`;
- tokens com expiração de 3600 segundos;
- issuer `https://nexapay.local/auth`;
- Resource Server JWT nos serviços protegidos;
- autorização com `@PreAuthorize`;
- métricas Prometheus via Actuator/Micrometer.

### JWT atual

A Sprint 5 usa **HS256 com segredo compartilhado** entre Auth e Resource Servers.

Isso simplifica o ambiente local, mas não é o modelo ideal para produção: um serviço com o segredo de verificação também possui material suficiente para assinar tokens.

Uma evolução recomendada é assinatura assimétrica, mantendo a chave privada somente no Auth Service e distribuindo somente chave pública/JWK aos Resource Servers.

---

# Sprint 6 — Resiliência

A Sprint 6 adiciona recuperação explícita para consumidores Kafka e fortalece idempotência sob concorrência.

### Política Kafka

```text
Tentativa inicial
      ↓ falha retriable
Retry 1 — backoff 1s
      ↓ falha
Retry 2 — backoff 1s
      ↓ falha
Dead Letter Topic
```

`max-retries=2` representa duas novas entregas após a tentativa inicial. Payloads classificados como inválidos não entram nessa sequência de retries.

### DLT como quarentena

A DLT é tratada como registro terminal de quarentena. Não existe consumer automático que republique indefinidamente mensagens problemáticas.

O utilitário:

```text
scripts/replay-dlt.ps1
```

seleciona explicitamente uma mensagem por marcador único. O comportamento padrão é **dry-run**; a republicação exige `-Replay`.

Exemplo de inspeção:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\replay-dlt.ps1 `
  -Route fraud-payment `
  -Marker "ACC-S6-FRAUD-EXEMPLO"
```

Replay explícito após corrigir a causa da falha:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\replay-dlt.ps1 `
  -Route fraud-payment `
  -Marker "ACC-S6-FRAUD-EXEMPLO" `
  -Replay
```

O registro original permanece na DLT como evidência de quarentena/auditoria; o replay publica uma nova cópia no tópico original, e a idempotência do consumidor define o efeito sobre o estado.

---

# Sprint 7 — Observabilidade

A Sprint 7 adiciona uma camada operacional completa sobre os cinco microsserviços.

### Métricas

Cada serviço expõe:

```http
GET /actuator/prometheus
```

O Prometheus coleta os cinco serviços e adiciona a tag `application` para identificar a origem das séries. Além das métricas padrão de JVM, processo e HTTP, o NexaPay possui métricas próprias para Outbox, decisões de fraude e resiliência Kafka.

### Retry e DLT

Ledger e Fraud registram métricas para:

```text
nexapay.kafka.delivery.failures
nexapay.kafka.retry.attempts
nexapay.kafka.dlt.published
nexapay.kafka.dlt.publish.failures
```

### Logs centralizados

Os serviços gravam arquivos em `logs/`. O Grafana Alloy coleta esses arquivos e envia para o Loki. O Grafana usa Loki como datasource para consulta centralizada.

### Dashboards provisionados

```text
NexaPay Overview
NexaPay Resilience
NexaPay Logs
```

A configuração é versionada em `observability/` e provisionada automaticamente pelo Docker Compose.

---

# Matriz de autorização

```text
Endpoint                                      Permission
POST /api/v1/payments/pix                     PAYMENT_CREATE
GET  /api/v1/payments/{id}                    PAYMENT_READ
POST /api/v1/accounts                         ACCOUNT_WRITE
POST /api/v1/accounts/{id}/credit             ACCOUNT_WRITE
POST /api/v1/accounts/{id}/debit              ACCOUNT_WRITE
GET  /api/v1/accounts/{id}                    ACCOUNT_READ
GET  /api/v1/ledger/accounts/{accountId}      LEDGER_READ
GET  /api/v1/fraud/payments/{paymentId}       FRAUD_READ
GET  /api/v1/auth/me                          AUTH_SELF_READ
```

### ROLE_USER

```text
AUTH_SELF_READ
PAYMENT_READ
PAYMENT_CREATE
ACCOUNT_READ
ACCOUNT_WRITE
LEDGER_READ
```

### ROLE_ADMIN

Recebe todas as permissões atuais, incluindo `FRAUD_READ`.

> A autorização atual é por endpoint/permission. Ainda não existe object-level authorization que garanta, por exemplo, que um usuário consulte somente a própria conta ou os próprios pagamentos.

---

# Como executar

## Pré-requisitos

- Java 21
- Maven
- Docker Desktop
- Git

```powershell
java -version
mvn -version
docker --version
docker compose version
git --version
```

## Infraestrutura

```powershell
docker compose up -d
```

```text
Kafka                 localhost:9092
Payment PostgreSQL    localhost:5435
Account PostgreSQL    localhost:5436
Ledger PostgreSQL     localhost:5437
Fraud PostgreSQL      localhost:5438
Auth PostgreSQL       localhost:5439
Prometheus            localhost:9090
Grafana               localhost:3000
Loki                  localhost:3100
Grafana Alloy         localhost:12345
```

Grafana local:

```text
usuário: admin
senha:   admin
```

## Aplicações

Em terminais separados:

```powershell
mvn -pl payment-service spring-boot:run
mvn -pl account-service spring-boot:run
mvn -pl ledger-service spring-boot:run
mvn -pl fraud-service spring-boot:run
mvn -pl auth-service spring-boot:run
```

```text
Payment Service   http://localhost:8081
Account Service   http://localhost:8082
Ledger Service    http://localhost:8083
Fraud Service     http://localhost:8084
Auth Service      http://localhost:8085
```

---

# Testes

Reactor completo:

```powershell
mvn clean test
```

Validação automatizada da Sprint 6:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint6-resilience.ps1
```

Resultado final validado:

```text
NEXAPAY SPRINT 6 VALIDATION
========================================
Full Maven reactor                         PASS
Ledger malformed payload -> DLT            PASS
Ledger DB failure -> retry/DLT             PASS
Fraud malformed payload -> DLT             PASS
Fraud DB failure -> retry/DLT               PASS
Controlled DLT replay                      PASS
```

Validação automatizada da Sprint 7:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint7-observability.ps1
```

A validação principal comprovou reactor Maven, endpoints Prometheus dos cinco serviços, cinco targets `UP`, métricas customizadas e provisioning do Grafana. O teste isolado do pipeline de logs é:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint7-loki.ps1
```

Resultado final do pipeline de logs:

```text
[OK] Validation file is visible inside Alloy.
[PASS] Loki + Alloy centralized logs
```

Com os resultados combinados, os 10 controles definidos para a Sprint 7 foram aprovados.

---

# Roadmap

## Sprint 1 — Payment Service ✅

- [x] API REST PIX
- [x] PostgreSQL/Flyway
- [x] Idempotency-Key
- [x] Transactional Outbox
- [x] Kafka
- [x] testes

## Sprint 2 — Account Service ✅

- [x] contas e saldo
- [x] crédito/débito
- [x] pessimistic locking
- [x] Transactional Outbox
- [x] Kafka
- [x] concorrência transacional

## Sprint 3 — Ledger Service ✅

- [x] Kafka Consumer
- [x] histórico financeiro
- [x] replay protection
- [x] paginação
- [x] testes

## Sprint 4 — Fraud Service ✅

- [x] Kafka Consumer de pagamentos
- [x] regras de risco
- [x] risk score
- [x] persistência
- [x] API de consulta
- [x] E2E Payment -> Kafka -> Fraud

## Sprint 5 — Segurança ✅

- [x] Auth Service
- [x] cadastro/login
- [x] password hashing
- [x] Spring Security
- [x] JWT
- [x] roles
- [x] permissions
- [x] Resource Server nos serviços REST
- [x] `401` / `403`
- [x] testes de autorização
- [x] E2E autenticado

## Sprint 6 — Resiliência ✅

- [x] Retry
- [x] Dead Letter Topic
- [x] estratégia de reprocessamento controlado
- [x] tratamento de mensagens inválidas
- [x] fortalecimento da idempotência concorrente
- [x] testes E2E de falha de banco
- [x] replay DLT após recuperação

## Sprint 7 — Observabilidade ✅

- [x] Micrometer / Actuator Prometheus
- [x] Prometheus com cinco targets
- [x] Grafana
- [x] métricas customizadas de Outbox
- [x] métricas de retry/DLT
- [x] métricas de fraude
- [x] Loki
- [x] Grafana Alloy
- [x] logs centralizados
- [x] dashboards Overview, Resilience e Logs
- [x] validação automatizada do stack

## Sprint 8 — API Gateway

- [ ] Spring Cloud Gateway
- [ ] roteamento
- [ ] autenticação centralizada
- [ ] Rate Limiting

## Sprint 9 — Frontend

- [ ] React
- [ ] Dashboard
- [ ] pagamentos
- [ ] contas
- [ ] movimentações
- [ ] decisões de fraude

## Sprint 10 — CI/CD e Cloud

- [ ] GitHub Actions
- [ ] pipeline
- [ ] empacotamento/deploy
- [ ] cloud

---

## Limitações conhecidas

- JWT usa HS256 com segredo compartilhado no ambiente atual;
- não há refresh token, revogação, password reset ou MFA;
- não há object-level authorization/ownership de conta ou pagamento;
- a decisão do Fraud Service não atualiza automaticamente o Payment Service;
- Kafka e Outbox operam com semântica at-least-once; não há garantia exactly-once global;
- DLT e offset commit não participam de uma única transação distribuída;
- o replay da DLT é operacional e controlado, não automático;
- o Ledger não é double-entry;
- tópicos/DLT devem ser provisionados em produção com contagem de partições compatível quando for preservada a partição original;
- credenciais do Grafana e segredos atuais são adequados somente para desenvolvimento local;
- não há API Gateway;
- CI/CD e cloud permanecem no roadmap.

---

## Autor

Projeto desenvolvido por **Jucelio Farias Coelho** como projeto de estudo e portfólio de engenharia de software backend Java.
