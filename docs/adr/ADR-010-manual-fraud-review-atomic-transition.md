# ADR-010 — Revisão manual de fraude por transição atômica

**Status:** Aceita

## Contexto

A Sprint 16 introduziu o estado `REVIEW` para pagamentos que exigem análise humana.

A resolução manual precisa ser segura contra duas decisões simultâneas e precisa preservar rastreabilidade.

## Decisão

Aplicar a decisão com conditional update:

```sql
UPDATE payments
SET status = :target_status,
    manual_review_decision = :decision,
    manual_review_reason = :reason,
    manual_reviewer_subject = :reviewer,
    manual_reviewed_at = :reviewed_at
WHERE id = :payment_id
  AND status = 'REVIEW';
```

`rowsUpdated == 1` define o vencedor.

A atualização do pagamento e a inserção da auditoria ocorrem na mesma transação local.

## Segurança

Introduzir `FRAUD_REVIEW`.

No modelo atual, apenas `ROLE_ADMIN` recebe essa authority automaticamente.

Uma futura evolução pode introduzir `ROLE_FRAUD_ANALYST`.

## Evento Kafka

Não publicar evento no v1.

A revisão manual fecha o estado do Payment Service e não precisa disparar integração adicional atualmente.

Se outros bounded contexts passarem a depender dessa decisão, será criado um contrato explícito via Outbox.

## Consequências

### Positivas

- um único vencedor em concorrência;
- histórico imutável;
- segregação de função;
- sem lock distribuído;
- sem evento prematuro.

### Custos

- nova permissão;
- nova tabela de auditoria;
- regra adicional de transição de estado.
