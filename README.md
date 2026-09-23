# NexaPay

<p align="center">
  <img src="docs/images/nexapay-logo.png" alt="NexaPay Logo" width="500"/>
</p>

<p align="center">
  <strong>Event-Driven Payment Platform</strong>
</p>

<p align="center">
  Plataforma de pagamentos distribuída construída com Java 21, Spring Boot, Apache Kafka, PostgreSQL, Transactional Outbox, Spring Security e observabilidade com Prometheus, Grafana, Loki, Alloy, OpenTelemetry e Grafana Tempo.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen" alt="Spring Boot 3.5.16"/>
  <img src="https://img.shields.io/badge/PostgreSQL-17-blue" alt="PostgreSQL 17"/>
  <img src="https://img.shields.io/badge/Apache%20Kafka-3.9.x-black" alt="Apache Kafka"/>
  <img src="https://img.shields.io/badge/Security-JWT-blueviolet" alt="JWT Security"/>
  <img src="https://img.shields.io/badge/Observability-Prometheus%20%7C%20Loki%20%7C%20Tempo-orange" alt="Observability"/>
  <img src="https://img.shields.io/badge/Sprint-12%20em%20evolu%C3%A7%C3%A3o-yellow" alt="Sprint 12 em evolução"/>
</p>

---


> **Engineering decisions & trade-offs:** [docs/engineering-decisions.md](docs/engineering-decisions.md) — contexto, alternativas consideradas, custos das escolhas, estratégia de testes e diagnóstico operacional.

> **AI-assisted engineering:** [AGENTS.md](AGENTS.md) · [PIX Payment SPEC](docs/specs/pix-payment-v1/README.md) · [ADR-005 SDD + AI](docs/adr/ADR-005-spec-driven-ai-assisted-development.md) · [AI workflows](.ai/workflows/feature-development.md)

### Engineering workflow: SDD + AI

O NexaPay usa IA como acelerador de engenharia, não como fonte de verdade. Mudanças relevantes seguem um fluxo verificável:

```text
Problem
  -> SPEC / acceptance criteria
  -> impact analysis + ADRs
  -> implementation
  -> automated tests
  -> review against SPEC
  -> CI / quality gates
  -> operational evidence
```

Guardrails para agentes, skills e workflows reutilizáveis são versionados junto com o código para manter contexto, invariantes e critérios de qualidade explícitos.

## Technical Snapshot

| Focus | Evidence in this project |
|---|---|
| Target roles | Java Backend Developer · Backend Engineer · Software Engineer |
| Architecture | Microservices · Event-Driven Architecture · Distributed Systems |
| Backend | Java 21 · Spring Boot · Spring Web · Spring Data JPA · Spring Security |
| Messaging & resilience | Apache Kafka · Transactional Outbox · Idempotency · Retry · DLT |
| Data | PostgreSQL · Redis · Flyway |
| Observability | OpenTelemetry · Prometheus · Grafana · Loki · Tempo |
| Quality & delivery | JUnit 5 · Mockito · MockMvc · Testcontainers · Docker · GitHub Actions |

**Engineering highlights:** fluxo PIX distribuído, processamento assíncrono, segurança JWT, semântica at-least-once com consumidores idempotentes, observabilidade ponta a ponta e tracing distribuído entre HTTP e Kafka.

**Keywords:** `Java Backend` `Spring Boot` `Microservices` `Apache Kafka` `REST API` `PostgreSQL` `Redis` `Docker` `CI/CD` `Distributed Systems` `Event-Driven Architecture` `Observability`

---


## Sobre o projeto

O **NexaPay** é um projeto de portfólio de engenharia de software backend Java voltado a sistemas financeiros distribuídos e orientados a eventos. A arquitetura explora comunicação síncrona e assíncrona, segurança, resiliência, CI/CD e os três pilares de observabilidade: **métricas, logs e traces**.

### Status

```text
Sprint 1  — Payment Service          ✅ Concluída
Sprint 2  — Account Service          ✅ Concluída
Sprint 3  — Ledger Service           ✅ Concluída
Sprint 4  — Fraud Service            ✅ Concluída
Sprint 5  — Segurança                ✅ Concluída
Sprint 6  — Resiliência              ✅ Concluída
Sprint 7  — Observabilidade          ✅ Concluída
Sprint 8  — API Gateway              ✅ Concluída
Sprint 9  — Frontend                 ✅ Concluída
Sprint 10 — CI/CD e Cloud            ✅ Concluída
Sprint 11 — Observabilidade avançada ✅ Concluída
Sprint 12 — Production Hardening     🚧 Em evolução
```

---

## Galeria do projeto

### Visão geral
<p align="center"><img src="docs/images/NEXA01.png" alt="NexaPay visão geral" width="900"/></p>

### Stack tecnológica
<p align="center"><img src="docs/images/NEXA02.png" alt="NexaPay stack tecnológica" width="900"/></p>

### Arquitetura e fluxo de eventos
<p align="center"><img src="docs/images/NEXA03.png" alt="NexaPay arquitetura" width="900"/></p>

### Evolução das sprints
<p align="center"><img src="docs/images/NEXA04.png" alt="NexaPay evolução das sprints" width="900"/></p>

### Frontend
<p align="center"><img src="docs/images/NEXA05.png" alt="NexaPay frontend" width="900"/></p>

### Ambiente integrado
<p align="center"><img src="docs/images/NEXA06.png" alt="NexaPay ambiente integrado" width="900"/></p>

### Containers Docker
<p align="center"><img src="docs/images/nexaDocker.png" alt="NexaPay containers Docker" width="900"/></p>

---

## Arquitetura

```text
Cliente
  |
  v
API Gateway :8080
  |
  +-------------------+
  |                   |
  v                   v
Auth :8085        Payment :8081
                      |
                 PostgreSQL
                      |
              Transactional Outbox
                      |
                      v
                 Apache Kafka
                      |
                      v
                 Fraud :8084

Account :8082 ---> Kafka ---> Ledger :8083

Observabilidade
  Metrics -> Micrometer -> Prometheus -> Grafana
  Logs    -> Structured JSON -> Alloy -> Loki -> Grafana
  Traces  -> OpenTelemetry / OTLP -> Tempo -> Grafana
```

### Semântica de eventos

Os produtores usam **Transactional Outbox** para persistir alteração de domínio e evento na mesma transação local. A publicação e o consumo Kafka trabalham com semântica **at-least-once**; por isso, os consumidores são projetados para idempotência e reprocessamento. O projeto não reivindica exactly-once global.

---

## Stack

### Backend
- Java 21
- Spring Boot 3.5.16
- Spring Web
- Spring Data JPA / Hibernate
- Spring Security
- OAuth2 Resource Server JWT
- Maven

### Dados e mensageria
- PostgreSQL 17
- Flyway
- Apache Kafka 3.9.x
- Transactional Outbox
- Retry com Spring Kafka
- Dead Letter Topics

### Observabilidade
- Spring Boot Actuator
- Micrometer
- Prometheus
- Grafana
- Loki
- Grafana Alloy
- OpenTelemetry
- OTLP
- Grafana Tempo
- TraceQL
- logs estruturados em JSON
- correlationId ponta a ponta
- métricas JVM, HTTP, Outbox, Kafka e fraude

### Testes e infraestrutura
- JUnit 5
- Mockito
- Spring Boot Test
- Spring Security Test
- MockMvc
- Testcontainers
- Docker / Docker Compose
- GitHub Actions
- CI/CD e build de imagens Docker

---

## Serviços

| Serviço | Porta | Responsabilidade |
|---|---:|---|
| API Gateway | 8080 | entrada, roteamento e segurança |
| Payment Service | 8081 | criação e consulta de pagamentos PIX |
| Account Service | 8082 | contas, crédito, débito e saldo |
| Ledger Service | 8083 | histórico financeiro via eventos Kafka |
| Fraud Service | 8084 | análise assíncrona de risco |
| Auth Service | 8085 | autenticação, JWT, roles e permissions |

### Payment Service

```http
POST /api/v1/payments/pix
GET  /api/v1/payments/{id}
```

A criação utiliza `Idempotency-Key`, Transactional Outbox e publica o evento:

```text
nexapay.payment.created.v1
```

### Account Service

```http
POST /api/v1/accounts
GET  /api/v1/accounts/{id}
POST /api/v1/accounts/{id}/credit
POST /api/v1/accounts/{id}/debit
```

Usa `BigDecimal`, transações, `PESSIMISTIC_WRITE` e publica eventos de crédito e débito via Outbox/Kafka.

### Ledger Service

Consome:

```text
nexapay.account.credited.v1
nexapay.account.debited.v1
```

Possui retry, DLT, replay protection e idempotência.

### Fraud Service

Consome:

```text
nexapay.payment.created.v1
```

```text
Valor < R$ 5.000                 -> APPROVED | score 20
R$ 5.000 <= valor < R$ 10.000   -> REVIEW   | score 70
Valor >= R$ 10.000              -> BLOCKED  | score 95
```

Possui retry/DLT, idempotência, métricas de fraude e tracing do consumer Kafka.

### Auth Service

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/auth/me
```

Implementa Spring Security, JWT, roles, permissions e Resource Server nos serviços protegidos.

---

# Sprint 11 — Observabilidade avançada ✅

A Sprint 11 consolidou métricas, logs estruturados, correlationId e SLOs técnicos.

- [x] correlationId ponta a ponta
- [x] propagação via Transactional Outbox e Kafka
- [x] fluxo Payment → Kafka → Fraud
- [x] logs estruturados em JSON
- [x] parsing com Grafana Alloy
- [x] structured metadata no Loki
- [x] dashboard NexaPay Distributed Logs
- [x] dashboard NexaPay Overview
- [x] dashboard NexaPay Resilience
- [x] dashboard NexaPay Technical SLOs
- [x] disponibilidade por serviço
- [x] HTTP 5xx Error Rate
- [x] latência HTTP
- [x] backlog do Outbox
- [x] métricas de retry e DLT Kafka

---

# Sprint 12 — Production Hardening e Distributed Tracing 🚧

## Production Hardening artifacts

- [Runbook, SLI/SLO targets and failure drills](docs/production-hardening.md)
- [Prometheus SLO recording/alert rules](observability/prometheus/slo-rules.yml)
- [Controlled failure-drill evidence script](scripts/failure-drill.ps1)
- [Controlled DLT replay](scripts/replay-dlt.ps1)

Current hardening status:

- [x] SLI/SLO targets versioned
- [x] bounded retry budget documented
- [x] Prometheus recording and alert rules
- [x] Prometheus rules validated by CI with `promtool`
- [x] PostgreSQL failure -> retry/DLT -> recovery/replay scenario
- [x] Kafka outage and downstream-service drill procedures
- [x] incident runbook
- [ ] runtime evidence package from the current environment (Grafana + Tempo + Loki + terminal output)

> The final evidence checkbox remains open intentionally until the drills are executed in the runtime environment and the resulting metrics/traces are reviewed.

A Sprint 12 evolui o NexaPay em direção a um ambiente mais próximo de produção, com foco em **distributed tracing**, diagnóstico de requisições e propagação de contexto através de comunicação síncrona e assíncrona.

## Distributed Tracing

O ambiente utiliza **Grafana Tempo** para armazenamento e consulta de traces distribuídos. A instrumentação permite acompanhar uma operação desde a entrada pelo API Gateway até o processamento assíncrono realizado pelo consumidor Kafka no Fraud Service.

### Fluxo validado

```text
Client
  |
  v
API Gateway
  | HTTP POST
  v
Payment Service
  |
  +-- POST /api/v1/payments/pix
  |
  +-- Transactional Outbox
  |
  +-- outbox publish PaymentCreated
  |
  +-- nexapay.payment.created.v1 send
          |
          v
       Apache Kafka
          |
          v
       Fraud Service
          |
          +-- nexapay.payment.created.v1 receive
```

### Evidência de tracing ponta a ponta

Foi validado no Grafana Tempo que uma operação PIX atravessa os seguintes componentes dentro da árvore de spans:

- [x] API Gateway
- [x] filtros Spring Security
- [x] Payment Service
- [x] `POST /api/v1/payments/pix`
- [x] autenticação Bearer Token
- [x] autorização do método
- [x] Transactional Outbox
- [x] `outbox publish PaymentCreated`
- [x] producer Kafka `nexapay.payment.created.v1 send`
- [x] propagação de contexto pelo Kafka
- [x] Fraud Service
- [x] consumer Kafka `nexapay.payment.created.v1 receive`

Exemplo conceitual da árvore observada:

```text
nexapay-gateway-service
└── http post
    └── nexapay-payment-service
        └── http post /api/v1/payments/pix
            ├── security filterchain
            ├── authenticate bearer token
            ├── secured request
            ├── authorize method
            ├── outbox publish PaymentCreated
            └── nexapay.payment.created.v1 send
                └── nexapay-fraud-service
                    └── nexapay.payment.created.v1 receive
```

A validação comprova propagação de contexto através de duas fronteiras diferentes:

```text
HTTP
Gateway -> Payment

Kafka
Payment -> Fraud
```

### Grafana Tempo e OTLP

```text
Grafana          localhost:3000
Tempo API        localhost:3200
OTLP gRPC        localhost:4317
OTLP HTTP        localhost:4318
```

O datasource `Tempo` permite explorar traces e executar consultas com **TraceQL**.

Exemplo por serviço:

```traceql
{ resource.service.name = "nexapay-gateway-service" }
```

Exemplo para localizar o consumer Kafka:

```traceql
{ name = "nexapay.payment.created.v1 receive" }
```

> Em uma busca por span filho, o resultado pode ser apresentado pelo span raiz do trace. O consumer Kafka pode então ser visualizado dentro da waterfall/árvore de spans do mesmo Trace ID.

### Três pilares de observabilidade

```text
Metrics
Services -> Micrometer -> Prometheus -> Grafana

Logs
Services -> Structured JSON -> Alloy -> Loki -> Grafana

Traces
Services -> OpenTelemetry / OTLP -> Tempo -> Grafana
```

Com essa evolução, o NexaPay permite correlacionar comportamento operacional, logs estruturados e execução distribuída entre microsserviços.

---

## Como executar

### Pré-requisitos

- Java 21
- Maven
- Docker Desktop
- Git

```powershell
docker compose up -d
```

### Infraestrutura local

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
Tempo API             localhost:3200
OTLP gRPC             localhost:4317
OTLP HTTP             localhost:4318
```

### Health checks

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8084/actuator/health
Invoke-RestMethod http://localhost:8085/actuator/health
```

Resultado esperado:

```text
status
------
UP
```

---

## Testes

```powershell
mvn clean test
```

Validação de resiliência:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint6-resilience.ps1
```

Validação de observabilidade:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint7-observability.ps1
```

---

## Limitações conhecidas

- JWT usa HS256 com segredo compartilhado no ambiente atual;
- não há refresh token, revogação, password reset ou MFA;
- não há object-level authorization/ownership de conta ou pagamento;
- a decisão do Fraud Service não atualiza automaticamente o Payment Service;
- Kafka e Outbox operam com semântica at-least-once;
- DLT e offset commit não participam de uma única transação distribuída;
- replay de DLT é operacional e controlado;
- o Ledger não é double-entry;
- credenciais e segredos locais devem ser endurecidos antes de produção;
- hardening de produção e deploy cloud real continuam como evoluções da Sprint 12.

---

## Autor

Projeto desenvolvido por **Jucelio Farias Coelho** como projeto de estudo e portfólio de engenharia de software backend Java, microsserviços, sistemas orientados a eventos, segurança, resiliência, CI/CD e observabilidade distribuída.
