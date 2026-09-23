# SPEC — Manual Fraud Review v1

**Status:** Sprint 17

## Objetivo

Fechar manualmente pagamentos colocados em `REVIEW` pelo Fraud Service.

Decisões suportadas:

- `APPROVE` -> `COMPLETED`
- `REJECT` -> `REJECTED`

## Endpoint

```http
POST /api/v1/payments/{paymentId}/fraud-review
Authorization: Bearer <token com FRAUD_REVIEW>
```

```json
{
  "decision": "APPROVE",
  "reason": "Documentação validada manualmente"
}
```

## Invariantes

- somente pagamento em `REVIEW` pode ser decidido manualmente;
- a decisão é aplicada por conditional update no PostgreSQL;
- duas revisões concorrentes não podem vencer;
- uma decisão vencedora gera auditoria imutável;
- o reviewer vem do subject do JWT;
- o motivo é obrigatório;
- a decisão original do Fraud Service permanece registrada;
- a revisão manual não publica um novo evento Kafka no v1.

## Concorrência

```text
REVIEW
  |
  +-- APPROVE claim --> COMPLETED
  |
  +-- REJECT claim ---> REJECTED
```

Somente um `UPDATE ... WHERE status = 'REVIEW'` pode afetar a linha.

## Segurança

Nova authority:

```text
FRAUD_REVIEW
```

No bootstrap atual, a permissão é concedida apenas a `ROLE_ADMIN`.

## Auditoria

Tabela `manual_fraud_review_audit`:

- id;
- payment_id;
- decision;
- reason;
- reviewer_subject;
- reviewed_at;
- previous_status;
- final_status.

## Observabilidade

Métricas:

- `nexapay.payment.manual_fraud_review.success{decision=...}`
- `nexapay.payment.manual_fraud_review.rejected`
- `nexapay.payment.manual_fraud_review.concurrent_conflict`

## Critérios de aceite

- [ ] APPROVE transforma REVIEW em COMPLETED;
- [ ] REJECT transforma REVIEW em REJECTED;
- [ ] PENDING não aceita revisão manual;
- [ ] COMPLETED/REJECTED não aceitam segunda revisão;
- [ ] duas decisões concorrentes têm um único vencedor;
- [ ] auditoria registra reviewer, motivo e transição;
- [ ] endpoint exige FRAUD_REVIEW;
- [ ] histórico exige PAYMENT_READ;
- [ ] Testcontainers valida concorrência em PostgreSQL real;
- [ ] CI permanece verde.
