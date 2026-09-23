# SPEC — Cancellation v1

**Status:** Sprint 15

## Objetivo

Permitir cancelamento seguro e auditável de:

- pagamento PIX agendado ainda não executado;
- regra de PIX recorrente ainda ativa.

## Endpoints

```http
POST /api/v1/payments/{paymentId}/cancel
Authorization: Bearer <token com PAYMENT_CANCEL>
```

```json
{
  "reason": "Cliente solicitou cancelamento"
}
```

```http
POST /api/v1/payments/pix/recurring/{scheduleId}/cancel
Authorization: Bearer <token com PAYMENT_CANCEL>
```

## Invariantes

### Pagamento agendado

- somente `SCHEDULED` pode ser cancelado;
- cancelamento e execução disputam atomicamente o mesmo estado;
- se cancelamento vencer: `SCHEDULED -> CANCELLED`;
- se execução vencer: `SCHEDULED -> PENDING` e o cancelamento falha;
- pagamento cancelado nunca gera `PaymentCreated`.

### Regra recorrente

- somente `ACTIVE` pode ser cancelada;
- cancelamento muda `ACTIVE -> CANCELLED`;
- `next_occurrence_at` é limpo;
- nenhuma nova ocorrência pode ser materializada;
- pagamentos já materializados e ainda `SCHEDULED` são cancelados na mesma transação;
- pagamentos já `PENDING` ou posteriores não são revertidos.

## Auditoria

Cada cancelamento vencedor cria uma linha imutável em `cancellation_audit`:

- target type;
- target id;
- reason;
- actor subject do JWT;
- cancelled at;
- quantidade de ocorrências materializadas canceladas, quando aplicável.

## Concorrência

### Cancelamento x execução

```text
SCHEDULED
   |
   +-- cancel atomic update --> CANCELLED
   |
   +-- execution claim ------> PENDING
```

Somente uma transição pode atualizar a linha.

### Cancelamento x materialização recorrente

```text
ACTIVE recurring schedule
   |
   +-- cancel ----------> CANCELLED
   |
   +-- materialize -----> ACTIVE/COMPLETED + Payment SCHEDULED
```

A transação vencedora serializa o resultado. Se a materialização já venceu, o cancelamento da regra também cancela seus pagamentos ainda `SCHEDULED`.

## Segurança

Nova authority:

```text
PAYMENT_CANCEL
```

Concedida aos papéis padrão USER e ADMIN no ambiente atual.

## Métricas

- `nexapay.payment.cancellation.success`
- `nexapay.payment.cancellation.rejected`
- `nexapay.payment.recurring.cancellation.success`
- `nexapay.payment.recurring.cancellation.rejected`
- `nexapay.payment.recurring.cancellation.materialized_payments`

## Critérios de aceite

- [ ] pagamento SCHEDULED pode ser cancelado;
- [ ] pagamento PENDING não pode ser cancelado;
- [ ] executor não consegue executar pagamento CANCELLED;
- [ ] corrida cancelamento x execução tem um único vencedor;
- [ ] regra ACTIVE pode ser cancelada;
- [ ] regra CANCELLED não materializa novas ocorrências;
- [ ] pagamentos materializados ainda SCHEDULED são cancelados com a regra;
- [ ] auditoria registra actor, motivo e horário;
- [ ] endpoint exige PAYMENT_CANCEL;
- [ ] Testcontainers valida concorrência em PostgreSQL real;
- [ ] CI permanece verde.
