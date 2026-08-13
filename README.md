# NexaPay — Event-Driven Payment Platform

Plataforma de pagamentos orientada a eventos para portfólio Java Backend.

## Sprint 1

Nesta primeira versão o projeto contém:

- Java 21
- Spring Boot 3.5.16
- API REST para criação e consulta de pagamentos PIX
- PostgreSQL
- Apache Kafka
- Idempotência por `Idempotency-Key`
- Transactional Outbox
- Flyway
- Bean Validation
- Tratamento global de erros
- JUnit 5 + Mockito
- Docker Compose
- Actuator

## Arquitetura inicial

```text
Cliente
  |
  v
Payment Service
  |
  +--> PostgreSQL
  |      |- payments
  |      `- outbox_events
  |
  `--> Outbox Publisher --> Kafka
                           |
                           `--> nexapay.payment.created.v1
```

## Pré-requisitos

- Java 21
- Maven 3.9+
- Docker Desktop

## Subir infraestrutura

```powershell
docker compose up -d
docker compose ps
```

## Executar o serviço

```powershell
mvn -pl payment-service spring-boot:run
```

API: http://localhost:8081

Health:

```text
GET http://localhost:8081/actuator/health
```

## Criar pagamento PIX

```http
POST /api/v1/payments/pix
Idempotency-Key: pedido-001
Content-Type: application/json
```

```json
{
  "payerAccountId": "ACC-1001",
  "pixKey": "cliente@email.com",
  "amount": 250.00,
  "description": "Pagamento NexaPay"
}
```

## Consultar pagamento

```http
GET /api/v1/payments/{id}
```

## Regra de idempotência

Enviar novamente a mesma chamada com o mesmo `Idempotency-Key`
não cria outro pagamento: o serviço devolve o pagamento original.

## Transactional Outbox

O pagamento e o evento são persistidos na mesma transação PostgreSQL.

Depois do commit, o `OutboxPublisher` envia o evento para Kafka e marca
o registro como publicado.

Se houver falha antes da publicação, o evento permanece pendente e será
tentado novamente.

### Importante

O publisher usa entrega pelo menos uma vez. Portanto, os futuros consumidores
também deverão ser idempotentes.

## Próximas sprints

1. Account Service + saldo e locking
2. Ledger Service com dupla entrada
3. Fraud Service consumindo Kafka
4. Auth Service + JWT
5. Retry/DLT
6. Testcontainers
7. Observabilidade
8. API Gateway
9. React Dashboard
10. CI/CD e deploy AWS
