# NexaPay

<p align="center">
  <img src="docs/images/nexapay-logo.png" alt="NexaPay Logo" width="460"/>
</p>

<p align="center">
  <strong>Event-Driven Payment Platform · Java 21 · Spring Boot · Kafka · React</strong>
</p>

<p align="center">
  Plataforma fintech full-stack de portfólio construída para demonstrar arquitetura distribuída, integração orientada a eventos, segurança com JWT/RBAC, resiliência, observabilidade e frontend React integrado por API Gateway.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Kafka-3.9.x-black" alt="Kafka"/>
  <img src="https://img.shields.io/badge/PostgreSQL-17-blue" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/React-19-61DAFB" alt="React 19"/>
  <img src="https://img.shields.io/badge/API%20Gateway-Spring%20Cloud-success" alt="API Gateway"/>
  <img src="https://img.shields.io/badge/Sprints-9%20conclu%C3%ADdas-success" alt="9 Sprints concluídas"/>
</p>

---

## Visão geral

O **NexaPay** simula uma plataforma de pagamentos PIX orientada a eventos. O projeto evoluiu em sprints incrementais e hoje reúne backend Java, mensageria Kafka, bancos dedicados por serviço, autenticação/autorização, API Gateway, rate limiting, observabilidade e uma SPA React profissional.

### Status atual

```text
Sprint 1 — Payment Service        ✅ Concluída
Sprint 2 — Account Service        ✅ Concluída
Sprint 3 — Ledger Service         ✅ Concluída
Sprint 4 — Fraud Service          ✅ Concluída
Sprint 5 — Segurança / Auth       ✅ Concluída
Sprint 6 — Resiliência / DLT      ✅ Concluída
Sprint 7 — Observabilidade        ✅ Concluída
Sprint 8 — API Gateway / Redis    ✅ Concluída
Sprint 9 — Frontend React         ✅ Concluída
Sprint 10 — CI/CD e Cloud         ⏳ Próxima
```

---

## Arquitetura atual

<p align="center">
  <img src="docs/images/nexapay-architecture.png" alt="Arquitetura NexaPay" width="100%"/>
</p>

```text
React / TypeScript / Vite :5173
            |
            v
Spring Cloud Gateway :8080
   |        |        |        |        |
   v        v        v        v        v
 Auth    Payment   Account  Ledger   Fraud
 :8085    :8081     :8082   :8083   :8084
   |        |          |       ^       ^
   |        | Outbox   | Outbox|       |
   |        +-------> Kafka ---+-------+
   |                    :9092
   |
PostgreSQL dedicado por serviço

Gateway -> JWT/RBAC + Redis Rate Limiting
Serviços -> Actuator/Micrometer -> Prometheus/Grafana
Logs -> Alloy -> Loki -> Grafana
```

### Semântica de eventos

Os produtores usam **Transactional Outbox** para persistir alteração de domínio e registro de evento na mesma transação local. A publicação e o consumo Kafka operam com semântica **at-least-once**. O projeto não reivindica exactly-once global.

---

## Stack

### Backend

- Java 21
- Spring Boot 3.5.16
- Spring Web / WebFlux
- Spring Data JPA / Hibernate
- Spring Security
- OAuth2 Resource Server JWT
- Spring Cloud Gateway
- Maven multi-module

### Dados e mensageria

- PostgreSQL 17
- Flyway
- Apache Kafka 3.9.x
- Transactional Outbox
- Retry + Dead Letter Topics
- Redis 7.4

### Frontend

- React 19
- TypeScript
- Vite
- React Router
- CSS customizado, responsivo e sem framework visual externo
- JWT em `sessionStorage`
- integração exclusiva pelo API Gateway no fluxo do navegador

### Observabilidade

- Spring Boot Actuator
- Micrometer
- Prometheus
- Grafana
- Loki
- Grafana Alloy

### Testes

- JUnit 5
- Mockito
- Spring Boot Test
- Spring Security Test
- MockMvc
- Testcontainers
- testes de concorrência
- validações E2E de Kafka, retry, DLT, Gateway e frontend

---

## Serviços

| Serviço | Porta | Responsabilidade |
|---|---:|---|
| API Gateway | 8080 | Entrada única, JWT, permissions, rate limiting e roteamento |
| Payment Service | 8081 | Criar/consultar PIX e publicar `payment.created` |
| Account Service | 8082 | Contas, saldo, crédito/débito e Outbox |
| Ledger Service | 8083 | Histórico derivado dos eventos de crédito/débito |
| Fraud Service | 8084 | Análise assíncrona de risco de pagamentos |
| Auth Service | 8085 | Cadastro, login, JWT, roles e permissions |
| Frontend | 5173 | Dashboard React e operações da plataforma |

---

## APIs principais

### Auth

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/auth/me
```

### Accounts

```http
POST /api/v1/accounts
GET  /api/v1/accounts/{id}
POST /api/v1/accounts/{id}/credit
POST /api/v1/accounts/{id}/debit
```

### Payments

```http
POST /api/v1/payments/pix
GET  /api/v1/payments/{id}
```

A criação de PIX exige `Idempotency-Key`.

### Ledger

```http
GET /api/v1/ledger/accounts/{accountId}
```

### Fraud

```http
GET /api/v1/fraud/payments/{paymentId}
```

A consulta antifraude exige `FRAUD_READ` e fica disponível ao perfil administrativo no modelo atual.

---

# Sprint 9 — Frontend React ✅

A Sprint 9 fecha a primeira experiência full-stack do NexaPay. O frontend consome contratos reais existentes, sem inventar endpoints globais de listagem para contas ou pagamentos.

### Entregas

- SPA React 19 + TypeScript + Vite;
- login e cadastro pelo API Gateway;
- separação entre requisições públicas e autenticadas;
- sessão JWT em `sessionStorage`;
- dashboard executivo com status do Gateway;
- criação e consulta de contas;
- crédito e débito de saldo;
- criação e consulta de PIX com `Idempotency-Key`;
- visualização do Ledger;
- análise antifraude protegida por `FRAUD_READ`;
- shell responsivo e visual profissional para demonstração;
- proxy Vite para `/api` e `/actuator` em desenvolvimento.

### Validação final da Sprint 9

Build de produção executado com sucesso:

```text
vite v8.2.1 building client environment for production...
✓ 91 modules transformed.
✓ built in 371ms
```

Validador funcional atual:

```text
NEXAPAY SPRINT 9 RUNTIME VALIDATION
========================================
Frontend dev server + SPA routes            PASS
Frontend proxy reaches Gateway              PASS
Register/login through frontend              PASS
JWT auth/me through frontend                 PASS
Account create/read through frontend         PASS
PIX create/read through frontend             PASS
Anonymous protection through frontend        PASS
```

Também foram validados manualmente no fluxo real de demonstração:

```text
Conta criada                              OK
Crédito R$ 1.000,00                       OK
Débito R$ 250,00                          OK
Saldo final R$ 750,00                     OK
Ledger CREDIT/DEBIT                        OK
PIX R$ 100,00                              OK
Fraud Service -> APPROVED / score 20       OK
RBAC ROLE_USER / ROLE_ADMIN                 OK
API Gateway                                 UP
```

---

## Evidências do teste PIX

### Dashboard após o fluxo de conta e PIX

O painel abaixo mostra o ambiente operacional após os testes, com saldo real consultado, conta acompanhada, pagamento acompanhado e Gateway operacional.

<p align="center">
  <img src="docs/images/nexapay-pix-dashboard-evidence.jpg" alt="NexaPay dashboard após teste PIX" width="100%"/>
</p>

### PIX processado pelo fluxo antifraude

Evidência end-to-end do pagamento PIX de **R$ 100,00**, consumido pelo Fraud Service e classificado como **APPROVED**, com **risk score 20/100**.

<p align="center">
  <img src="docs/images/nexapay-pix-antifraud-evidence.jpg" alt="NexaPay PIX aprovado pelo Fraud Service" width="100%"/>
</p>

Fluxo demonstrado:

```text
React
  -> API Gateway
  -> Payment Service
  -> Transactional Outbox
  -> Kafka
  -> Fraud Service
  -> PostgreSQL
  -> React / decisão antifraude
```

---

## Segurança e autorização

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

O Gateway centraliza autenticação/autorização de entrada, mas os serviços downstream continuam protegidos como Resource Servers — defesa em profundidade.

---

## Como executar localmente

### 1. Infraestrutura

```powershell
docker compose up -d postgres postgres-accounts postgres-ledger postgres-fraud postgres-auth kafka redis
```

### 2. Serviços

Em terminais separados:

```powershell
mvn -pl auth-service spring-boot:run
mvn -pl account-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl ledger-service spring-boot:run
mvn -pl fraud-service spring-boot:run
mvn -pl gateway-service spring-boot:run
```

### 3. Frontend

```powershell
cd frontend
npm install
npm run dev
```

Acesse:

```text
Frontend     http://localhost:5173
API Gateway  http://localhost:8080
```

### 4. Build de produção

```powershell
cd frontend
npm run build
```

### 5. Validação funcional da Sprint 9

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint9-runtime.ps1
```

---

## Roadmap

### Sprint 10 — CI/CD e Cloud

- [ ] GitHub Actions
- [ ] build/test automatizado do reactor Maven
- [ ] build automatizado do frontend
- [ ] empacotamento de serviços
- [ ] estratégia de deploy
- [ ] ambiente cloud
- [ ] configuração de frontend para produção

---

## Limitações conhecidas

- JWT usa HS256 com segredo compartilhado no ambiente local atual;
- não há refresh token, revogação, password reset ou MFA;
- não há object-level authorization/ownership de conta ou pagamento;
- a decisão do Fraud Service não atualiza automaticamente o Payment Service;
- Kafka/Outbox operam com semântica at-least-once;
- o Ledger não é double-entry;
- não há tracing distribuído/OpenTelemetry implementado;
- observabilidade e credenciais atuais são adequadas a desenvolvimento/portfólio, não a produção;
- o frontend em desenvolvimento usa proxy Vite; deploy separado exigirá reverse proxy same-origin ou política CORS adequada.

---

## Autor

**Jucelio Farias Coelho**  
Java Backend · Spring Boot · Kafka · PostgreSQL · React · Sistemas Distribuídos

GitHub: `juceliocoelho2022`
