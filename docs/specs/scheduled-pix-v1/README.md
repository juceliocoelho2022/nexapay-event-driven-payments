# SPEC — Scheduled PIX v1

**Status:** Sprint 13 — Fase 1  
**Escopo:** agendamento único (one-shot) de PIX  
**Fora de escopo deste PR:** recorrência periódica

## 1. Problema

O cliente precisa registrar hoje um PIX para execução futura sem perder as garantias já existentes do NexaPay: idempotência, Transactional Outbox, Kafka at-least-once e observabilidade.

## 2. Contrato HTTP

```http
POST /api/v1/payments/pix/scheduled
Idempotency-Key: <client-generated-key>
```

Payload:

```json
{
  "payerAccountId": "ACC-1001",
  "pixKey": "destino@nexapay.test",
  "amount": 150.00,
  "description": "Pagamento agendado",
  "scheduledAt": "2026-09-24T10:00:00-03:00"
}
```

## 3. Comportamento esperado

### Criação

- valida `scheduledAt` no futuro;
- reserva a `Idempotency-Key` de forma atômica;
- persiste o pagamento com status `SCHEDULED`;
- **não** publica `PaymentCreated` no momento do agendamento.

### Execução

Quando `scheduledAt <= now`:

1. o scheduler encontra candidatos vencidos;
2. uma atualização condicional reivindica o pagamento;
3. somente a instância vencedora altera `SCHEDULED -> PENDING`;
4. na mesma transação local, cria o evento na Transactional Outbox;
5. o pipeline Outbox/Kafka existente publica `nexapay.payment.created.v1`.

## 4. Invariantes

- uma chave idempotente não pode criar dois agendamentos;
- múltiplas instâncias do scheduler não podem produzir dois eventos para o mesmo pagamento;
- não há dual write banco + Kafka;
- pagamento futuro não pode ser executado antes de `scheduledAt`;
- retry/redelivery Kafka continua sendo tratado como at-least-once;
- valores monetários continuam em `BigDecimal`.

## 5. Estados

```text
SCHEDULED
   |
   | scheduledAt <= now + atomic claim
   v
PENDING
   |
   +--> fluxo assíncrono existente
```

## 6. Persistência

Adicionar em `payments`:

- `scheduled_at TIMESTAMPTZ NULL`
- `executed_at TIMESTAMPTZ NULL`

Índice para busca eficiente dos pagamentos vencidos.

## 7. Concorrência

A execução não depende apenas de `SELECT`.

A reivindicação é feita com:

```text
UPDATE payments
SET status = 'PENDING', executed_at = :now
WHERE id = :id
  AND status = 'SCHEDULED'
  AND scheduled_at <= :now
```

Somente quem obtiver uma linha atualizada cria a Outbox.

## 8. Observabilidade

Métricas mínimas:

- `nexapay.payment.scheduled`
- `nexapay.payment.scheduled.executed`
- `nexapay.payment.scheduled.claim.skipped`

O evento executado reutiliza correlation/trace context disponível; quando a execução não possui contexto HTTP ativo, o eventId funciona como correlationId de fallback.

## 9. Critérios de aceite

- [ ] endpoint exige `PAYMENT_CREATE`;
- [ ] `scheduledAt` deve estar no futuro;
- [ ] criação retorna status `SCHEDULED`;
- [ ] duplicidade de `Idempotency-Key` retorna o pagamento existente;
- [ ] pagamento não gera Outbox na criação do agendamento;
- [ ] pagamento vencido é reivindicado atomicamente;
- [ ] apenas o vencedor da reivindicação cria a Outbox;
- [ ] pagamento executado passa para `PENDING`;
- [ ] `executedAt` é registrado;
- [ ] testes unitários cobrem criação, idempotência e execução;
- [ ] CI permanece verde.

## 10. Próxima fase

Após estabilizar este slice:

```text
Scheduled PIX v1
      |
      v
Recurring Schedule
(daily/weekly/monthly)
      |
      v
materialização de cada execução como pagamento independente
```
