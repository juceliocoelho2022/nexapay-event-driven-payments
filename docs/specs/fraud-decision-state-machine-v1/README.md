# SPEC — Fraud Decision + Payment State Machine v1

**Status:** Sprint 16

## Objetivo

Fechar o ciclo assíncrono:

```text
Payment Service
  PENDING
    |
    | PaymentCreated
    v
Kafka
    |
    v
Fraud Service
    |
    | FraudDecisionMade
    v
Kafka
    |
    v
Payment Service
  final state
```

## Decisões de fraude

| Fraud decision | Payment state |
|---|---|
| APPROVED | COMPLETED |
| REVIEW | REVIEW |
| BLOCKED | REJECTED |

Somente pagamentos em `PENDING` podem receber uma decisão de fraude.

## Evento de saída do Fraud Service

Topic:

```text
nexapay.fraud.decision-made.v1
```

Payload:

```json
{
  "eventId": "uuid",
  "fraudDecisionId": "uuid",
  "paymentId": "uuid",
  "decision": "APPROVED",
  "riskScore": 20,
  "reason": "Payment amount is within the normal risk range",
  "occurredAt": "2026-09-23T12:00:00Z"
}
```

## Garantias

- a decisão de fraude e sua Outbox são persistidas na mesma transação local;
- duplicidade de `PaymentCreated` não cria duas decisões nem duas Outboxes;
- publicação Kafka permanece at-least-once;
- o Payment Service registra eventos de fraude já processados;
- duplicidade de `FraudDecisionMade` não reaplica a transição;
- somente `PENDING` pode evoluir por decisão de fraude;
- evento inesperado para estado diferente causa falha explícita, permitindo retry/DLT.

## Persistência no Payment Service

Adicionar a `payments`:

- `fraud_decision`
- `fraud_risk_score`
- `fraud_reason`
- `fraud_decided_at`

Nova tabela `processed_fraud_decision_events`:

- `event_id` PK;
- `fraud_decision_id` UNIQUE;
- `payment_id`;
- `processed_at`.

## Idempotência

O consumidor primeiro tenta reservar o evento em
`processed_fraud_decision_events`.

```text
insert == 0 -> duplicate -> no-op
insert == 1 -> attempt PENDING -> target state
```

Reserva e transição ocorrem na mesma transação.

## Observabilidade

Métricas:

- `nexapay.fraud.outbox.created`
- `nexapay.fraud.outbox.published`
- `nexapay.payment.fraud_decision.applied{decision=...}`
- `nexapay.payment.fraud_decision.duplicate`
- `nexapay.payment.fraud_decision.invalid_state`

## Critérios de aceite

- [ ] APPROVED transforma PENDING em COMPLETED;
- [ ] REVIEW transforma PENDING em REVIEW;
- [ ] BLOCKED transforma PENDING em REJECTED;
- [ ] Fraud Service cria Outbox somente para a decisão vencedora;
- [ ] decisão e Outbox são transacionais;
- [ ] duplicidade do evento de retorno é ignorada;
- [ ] decisão concorrente não causa dupla transição;
- [ ] decisão recebida em estado incompatível falha explicitamente;
- [ ] trace/correlation context é preservado na Outbox;
- [ ] Testcontainers valida ambos os lados com PostgreSQL real;
- [ ] CI permanece verde.
