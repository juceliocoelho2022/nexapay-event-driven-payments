# SPEC — Fraud Review Queue v1

**Status:** Sprint 18

## Objetivo

Transformar pagamentos em `REVIEW` em casos operacionais de análise com ownership temporário.

## Fluxo

```text
FraudDecisionMade(REVIEW)
        |
        v
Payment REVIEW
        |
        +--> FraudReviewCase OPEN
                  |
                  | analyst claim (lease 15 min)
                  v
              CLAIMED
                  |
                  +-- APPROVE --> Payment COMPLETED
                  |
                  +-- REJECT ---> Payment REJECTED
                  |
                  +-- release / lease expiry --> available again
```

## Endpoints

```http
GET  /api/v1/fraud-review/cases
POST /api/v1/fraud-review/cases/{paymentId}/claim
POST /api/v1/fraud-review/cases/{paymentId}/release
```

Todos exigem `FRAUD_REVIEW`.

A decisão continua usando:

```http
POST /api/v1/payments/{paymentId}/fraud-review
```

mas passa a exigir ownership ativo do caso.

## Lease

Duração padrão:

```text
15 minutos
```

Configurável por:

```text
nexapay.payment.fraud-review.lease-duration
```

Um claim pode ser adquirido quando:

- o caso está `OPEN`;
- não possui owner;
- o lease expirou;
- ou o próprio owner renova o claim.

## Concorrência

Claim:

```sql
UPDATE fraud_review_cases
SET claimed_by = :reviewer,
    claimed_at = :now,
    claim_expires_at = :expires_at
WHERE payment_id = :payment_id
  AND status = 'OPEN'
  AND (
      claimed_by IS NULL
      OR claimed_by = :reviewer
      OR claim_expires_at <= :now
  );
```

Dois analistas concorrentes para o mesmo caso: somente um pode vencer enquanto o lease estiver ativo.

## Decisão manual

A Sprint 17 continua responsável pela transição:

```text
REVIEW -> COMPLETED
REVIEW -> REJECTED
```

Na Sprint 18, a transação também exige:

- caso `OPEN`;
- `claimed_by == reviewerSubject`;
- lease ainda válido.

A resolução do caso e a decisão do pagamento fazem parte da mesma transação local.

## Persistência

Nova tabela `fraud_review_cases`:

- `payment_id` PK/FK;
- `status` OPEN/RESOLVED;
- `opened_at`;
- `claimed_by`;
- `claimed_at`;
- `claim_expires_at`;
- `resolved_at`;
- `resolution`.

A migration faz backfill para pagamentos que já estiverem em `REVIEW`.

## Fila

A listagem retorna casos OPEN ordenados por `opened_at`, incluindo:

- payment id;
- amount;
- pix key;
- risk score;
- fraud reason;
- owner atual;
- expiração do lease;
- indicador de disponibilidade.

## Observabilidade

Métricas:

- `nexapay.payment.fraud_review_queue.opened`
- `nexapay.payment.fraud_review_queue.claimed`
- `nexapay.payment.fraud_review_queue.claim_conflict`
- `nexapay.payment.fraud_review_queue.released`
- `nexapay.payment.fraud_review_queue.resolved{resolution=...}`

## Critérios de aceite

- [ ] decisão REVIEW abre um caso automaticamente;
- [ ] APPROVED/BLOCKED não abrem caso;
- [ ] dois analistas concorrentes têm um único vencedor;
- [ ] mesmo analista pode renovar o lease;
- [ ] lease expirado pode ser assumido por outro analista;
- [ ] somente owner com lease válido pode decidir;
- [ ] decisão resolve caso e pagamento na mesma transação;
- [ ] release devolve caso à fila;
- [ ] fila exige FRAUD_REVIEW;
- [ ] Testcontainers valida concorrência e expiração;
- [ ] CI permanece verde.
