# NexaPay

<p align="center">
  <img src="docs/images/nexapay-logo.png" alt="NexaPay Logo" width="500"/>
</p>

<p align="center">
  <strong>Event-Driven Payment Platform</strong>
</p>

<p align="center">
  Plataforma de pagamentos orientada a eventos desenvolvida com Java 21, Spring Boot, Apache Kafka, PostgreSQL e Transactional Outbox.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/PostgreSQL-17-blue" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/Apache%20Kafka-3.9-black" alt="Kafka"/>
  <img src="https://img.shields.io/badge/Docker-Compose-blue" alt="Docker"/>
</p>

---

## Sobre o projeto

O **NexaPay** é uma plataforma de pagamentos criada para demonstrar conhecimentos de desenvolvimento backend Java aplicados a um cenário de sistemas financeiros distribuídos.

A arquitetura foi pensada para evoluir progressivamente para microsserviços orientados a eventos.

A primeira Sprint implementa:

- API REST para pagamentos PIX;
- Java 21;
- Spring Boot;
- PostgreSQL;
- Spring Data JPA;
- Hibernate;
- Apache Kafka;
- Docker Compose;
- Flyway;
- Bean Validation;
- Idempotency-Key;
- Transactional Outbox;
- tratamento global de exceções;
- JUnit 5;
- Mockito;
- Spring Boot Actuator.

### Status

**Sprint 1 concluída.**

O fluxo:

```text
API REST
   |
   v
Payment Service
   |
   +--> PostgreSQL
   |
   +--> Transactional Outbox
             |
             v
        Apache Kafka
```

já está funcionando.

---

## Arquitetura

A imagem abaixo apresenta a arquitetura planejada para evolução do NexaPay.

<p align="center">
  <img src="docs/images/nexapay-architecture.png" alt="Arquitetura NexaPay"/>
</p>

> Alguns componentes exibidos no diagrama ainda fazem parte do roadmap e serão implementados nas próximas Sprints.

### Arquitetura atual

```text
Cliente
   |
   v
PaymentController
   |
   v
PaymentService
   |
   +-----------------------+
   |                       |
   v                       v
PostgreSQL            Outbox Events
                           |
                           v
                    OutboxPublisher
                           |
                           v
                     Apache Kafka
                           |
                           v
              nexapay.payment.created.v1
```

---

# Stack

## Backend

- Java 21
- Spring Boot 3.5.16
- Spring Web
- Spring Data JPA
- Hibernate
- Bean Validation
- Maven

## Banco de Dados

- PostgreSQL 17
- Flyway
- JPA/Hibernate

## Mensageria

- Apache Kafka
- Kafka Producer
- Event-Driven Architecture
- Transactional Outbox

## Testes

- JUnit 5
- Mockito

## Infraestrutura

- Docker
- Docker Compose
- Spring Boot Actuator

## Conceitos aplicados

- REST API
- Idempotência
- DTO
- Repository Pattern
- Service Layer
- tratamento global de exceções
- arquitetura em camadas
- eventos assíncronos
- consistência transacional
- at-least-once delivery

---

# Funcionalidades

## Criar pagamento PIX

Endpoint:

```http
POST /api/v1/payments/pix
```

Header obrigatório:

```http
Idempotency-Key: pedido-001
Content-Type: application/json
```

Body:

```json
{
  "payerAccountId": "ACC-1001",
  "pixKey": "cliente@email.com",
  "amount": 250.00,
  "description": "Pagamento NexaPay"
}
```

Exemplo de resposta:

```json
{
  "id": "7014a517-0738-4d49-9db4-9578ced44089",
  "payerAccountId": "ACC-1001",
  "pixKey": "cliente@email.com",
  "amount": 250.00,
  "description": "Pagamento NexaPay",
  "status": "PENDING",
  "createdAt": "2026-08-13T21:57:51.521587Z"
}
```

---

## Consultar pagamento

```http
GET /api/v1/payments/{id}
```

Exemplo:

```http
GET /api/v1/payments/7014a517-0738-4d49-9db4-9578ced44089
```

---

# Idempotência

O NexaPay utiliza o header:

```http
Idempotency-Key
```

para impedir a criação duplicada de pagamentos.

Exemplo:

```text
Primeira requisição

pedido-001
   |
   v
Pagamento criado
   |
   v
payment-id-123


Segunda requisição

pedido-001
   |
   v
Pagamento já existe
   |
   v
payment-id-123
```

Isso protege o sistema contra situações como:

- clique duplo;
- timeout do cliente;
- retry automático;
- problemas de rede;
- reenvio acidental da mesma operação.

---

# Transactional Outbox

Um dos principais recursos técnicos do NexaPay é o uso do padrão:

**Transactional Outbox**

O pagamento e o evento são gravados dentro da mesma transação.

```text
@Transactional
      |
      +--> INSERT payments
      |
      +--> INSERT outbox_events
      |
      v
    COMMIT
```

Depois do commit:

```text
outbox_events
      |
      v
OutboxPublisher
      |
      v
Apache Kafka
      |
      v
published = true
```

Isso evita um problema clássico de sistemas distribuídos:

```text
Pagamento salvo no banco
        |
        X
Kafka falhou antes da publicação
```

Com Outbox:

```text
Pagamento
    +
Evento

salvos na mesma transação
```

O evento pode ser publicado posteriormente caso Kafka esteja temporariamente indisponível.

---

# Kafka

Tópico utilizado:

```text
nexapay.payment.created.v1
```

Exemplo de evento:

```json
{
  "eventId": "320df5c4-0299-4254-ac7f-a3e268f42b91",
  "paymentId": "7014a517-0738-4d49-9db4-9578ced44089",
  "payerAccountId": "ACC-1001",
  "pixKey": "cliente@email.com",
  "amount": 250.00,
  "occurredAt": "2026-08-13T21:57:51.521587Z"
}
```

O publisher trabalha com:

```text
at-least-once delivery
```

Por isso os próximos consumidores também deverão ser idempotentes.

---

# Banco de Dados

## payments

Tabela responsável pelos pagamentos.

Principais campos:

```text
id
idempotency_key
payer_account_id
pix_key
amount
description
status
created_at
```

## outbox_events

Tabela responsável pelos eventos que serão publicados no Kafka.

Principais campos:

```text
id
aggregate_id
event_type
payload
published
created_at
published_at
```

---

# Estrutura do Projeto

```text
nexapay-event-driven-payments
|
├── docs
│   └── images
│       ├── nexapay-logo.png
│       └── nexapay-architecture.png
│
├── payment-service
│   │
│   ├── src
│   │   │
│   │   ├── main
│   │   │   │
│   │   │   ├── java
│   │   │   │   └── br
│   │   │   │       └── com
│   │   │   │           └── nexapay
│   │   │   │               └── payment
│   │   │   │                   ├── api
│   │   │   │                   ├── domain
│   │   │   │                   ├── event
│   │   │   │                   ├── exception
│   │   │   │                   ├── messaging
│   │   │   │                   ├── repository
│   │   │   │                   └── service
│   │   │   │
│   │   │   └── resources
│   │   │       ├── db
│   │   │       │   └── migration
│   │   │       └── application.yml
│   │   │
│   │   └── test
│   │
│   └── pom.xml
│
├── scripts
│   ├── run-payment.ps1
│   ├── start-infra.ps1
│   ├── stop-infra.ps1
│   └── test-payment.ps1
│
├── docker-compose.yml
├── pom.xml
├── .gitignore
└── README.md
```

---

# Como Executar

## Pré-requisitos

Instale:

- Java 21
- Maven
- Docker Desktop
- Git

Confira:

```powershell
java -version
mvn -version
docker --version
docker compose version
git --version
```

---

## 1. Clonar o projeto

```powershell
git clone https://github.com/juceliocoelho2022/nexapay-event-driven-payments.git
```

Entre na pasta:

```powershell
cd nexapay-event-driven-payments
```

---

## 2. Subir PostgreSQL e Kafka

```powershell
docker compose up -d
```

Confira:

```powershell
docker compose ps
```

Containers esperados:

```text
nexapay-postgres
nexapay-kafka
```

Portas:

```text
PostgreSQL

Windows:
localhost:5435

Container:
5432


Kafka:

localhost:9092
```

---

## 3. Executar Payment Service

```powershell
mvn -pl payment-service spring-boot:run
```

API:

```text
http://localhost:8081
```

---

# Health Check

No navegador:

```text
http://localhost:8081/actuator/health
```

Ou PowerShell:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

Resultado esperado:

```json
{
  "status": "UP"
}
```

---

# Testar Pagamento PIX

O projeto possui um script PowerShell para realizar um pagamento.

Execute:

```powershell
.\scripts\test-payment.ps1
```

Resultado esperado:

```text
id             : UUID
payerAccountId : ACC-1001
pixKey         : cliente@email.com
amount         : 250,00
description    : Pagamento de teste NexaPay
status         : PENDING
createdAt      : data-hora
```

---

# Validar PostgreSQL

Entre no PostgreSQL:

```powershell
docker exec -it nexapay-postgres psql -U nexapay -d nexapay_payments
```

Consultar pagamentos:

```sql
SELECT
    id,
    idempotency_key,
    payer_account_id,
    pix_key,
    amount,
    status,
    created_at
FROM public.payments;
```

Consultar Outbox:

```sql
SELECT
    id,
    aggregate_id,
    event_type,
    published,
    created_at,
    published_at
FROM public.outbox_events;
```

Para sair:

```sql
\q
```

---

# Validar Kafka

Listar tópicos:

```powershell
docker exec nexapay-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Resultado esperado:

```text
nexapay.payment.created.v1
```

Consumir mensagens:

```powershell
docker exec -it nexapay-kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic nexapay.payment.created.v1 `
  --from-beginning `
  --property print.key=true
```

Para encerrar:

```text
Ctrl + C
```

---

# Testes

Executar:

```powershell
mvn -pl payment-service test
```

Testes existentes incluem:

- criação de pagamento;
- idempotência;
- persistência do Outbox Event;
- comportamento do Payment Service.

---

# Observabilidade

Atualmente o projeto utiliza:

**Spring Boot Actuator**

Endpoints:

```text
/actuator/health
/actuator/info
/actuator/metrics
```

Planejado:

- Prometheus;
- Grafana;
- Loki.

---

# Segurança

A autenticação ainda não faz parte da Sprint 1.

Está planejada a utilização de:

- Spring Security;
- JWT;
- usuários;
- roles;
- permissions;
- API Gateway.

---

# Roadmap

## Sprint 1 — Payment Service ✅

- [x] Java 21
- [x] Spring Boot
- [x] REST API
- [x] PostgreSQL
- [x] Flyway
- [x] Kafka
- [x] Docker Compose
- [x] Idempotency-Key
- [x] Transactional Outbox
- [x] JUnit
- [x] Mockito
- [x] Actuator

---

## Sprint 2 — Account Service

- [ ] Account Service
- [ ] criação de contas
- [ ] saldo
- [ ] débito
- [ ] crédito
- [ ] pessimistic locking
- [ ] concorrência transacional
- [ ] eventos Kafka

---

## Sprint 3 — Ledger Service

- [ ] Ledger Service
- [ ] dupla entrada
- [ ] movimentações financeiras imutáveis
- [ ] histórico de lançamentos
- [ ] conciliação

---

## Sprint 4 — Fraud Service

- [ ] Fraud Service
- [ ] Kafka Consumer
- [ ] regras antifraude
- [ ] aprovação
- [ ] rejeição
- [ ] auditoria

---

## Sprint 5 — Segurança

- [ ] Auth Service
- [ ] Spring Security
- [ ] JWT
- [ ] Roles
- [ ] Permissions

---

## Sprint 6 — Resiliência

- [ ] Retry
- [ ] Dead Letter Topic
- [ ] reprocessamento
- [ ] consumidores idempotentes

---

## Sprint 7 — Observabilidade

- [ ] Prometheus
- [ ] Grafana
- [ ] Loki
- [ ] métricas
- [ ] dashboards

---

## Sprint 8 — API Gateway

- [ ] Spring Cloud Gateway
- [ ] roteamento
- [ ] autenticação centralizada
- [ ] Rate Limiting

---

## Sprint 9 — Frontend

- [ ] React
- [ ] Dashboard
- [ ] pagamentos
- [ ] transações
- [ ] monitoramento

---

## Sprint 10 — CI/CD e Cloud

- [ ] GitHub Actions
- [ ] CI/CD
- [ ] testes automatizados
- [ ] Docker Images
- [ ] AWS

---

# Stack Futura

A arquitetura-alvo poderá utilizar:

```text
Java 21
Spring Boot
Spring Security
Spring Cloud Gateway
JWT

PostgreSQL
Redis
MongoDB

Apache Kafka

Docker
Docker Compose

Prometheus
Grafana
Loki

GitHub Actions
AWS

React
```

---

# Decisões Arquiteturais

## Por que BigDecimal?

Valores monetários não devem utilizar:

```java
double
```

O NexaPay utiliza:

```java
BigDecimal
```

para evitar problemas de precisão em operações financeiras.

---

## Por que Idempotency-Key?

Uma requisição de pagamento pode ser enviada mais de uma vez devido a:

- timeout;
- retry;
- problemas de rede;
- clique duplicado.

O `Idempotency-Key` permite identificar que a operação já foi processada.

---

## Por que Transactional Outbox?

Sem Outbox poderia ocorrer:

```text
Banco salva pagamento
        |
        v
Aplicação tenta publicar Kafka
        |
        X
Kafka indisponível
```

Resultado:

```text
Pagamento existe

mas

evento não existe
```

Com Outbox:

```text
Pagamento
+
Evento

persistidos na mesma transação
```

Isso melhora a consistência do sistema.

---

## Por que Kafka?

Kafka permite:

- desacoplamento;
- comunicação assíncrona;
- processamento distribuído;
- escalabilidade;
- eventos;
- integração entre microsserviços.

---

## Por que Flyway?

Flyway mantém as alterações do banco versionadas junto ao código.

Exemplo:

```text
V1__create_payment_and_outbox.sql
V2__...
V3__...
```

---

# Objetivos Técnicos

O NexaPay foi criado para estudar e demonstrar:

- Java Backend;
- Spring Boot;
- APIs REST;
- PostgreSQL;
- mensageria;
- Apache Kafka;
- arquitetura orientada a eventos;
- Transactional Outbox;
- idempotência;
- microsserviços;
- concorrência;
- consistência;
- resiliência;
- testes;
- observabilidade;
- segurança;
- Docker;
- CI/CD;
- cloud.

---

# Autor

**Jucelio Farias Coelho**

Desenvolvedor Java Backend

Stack principal:

```text
Java
Spring Boot
Apache Kafka
PostgreSQL
Docker
```

GitHub:

```text
https://github.com/juceliocoelho2022
```

Projeto:

```text
https://github.com/juceliocoelho2022/nexapay-event-driven-payments
```

---

<p align="center">
  <strong>NexaPay — Event-Driven Payment Platform</strong>
</p>

<p align="center">
  Java 21 • Spring Boot • Apache Kafka • PostgreSQL • Docker
</p>