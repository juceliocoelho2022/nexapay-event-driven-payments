# SPEC — Recurring PIX v1

**Status:** Sprint 14  
**Escopo:** recorrência finita diária, semanal e mensal

## Problema

O cliente precisa cadastrar uma regra de PIX recorrente sem duplicar a lógica de execução da Sprint 13.

A recorrência deve definir **quando materializar pagamentos**. Cada ocorrência materializada vira um pagamento `SCHEDULED` normal e passa pelo fluxo já validado:

```text
Recurring Schedule
      |
      v
materializa ocorrência
      |
      v
Payment SCHEDULED
      |
      v
claim atômico
      |
      v
PENDING + Transactional Outbox
      |
      v
Kafka
```

## API

```http
POST /api/v1/payments/pix/recurring
Idempotency-Key: <client-generated-key>
```

Exemplo:

```json
{
  "payerAccountId": "ACC-1001",
  "pixKey": "destino@nexapay.test",
  "amount": 199.90,
  "description": "Mensalidade",
  "frequency": "MONTHLY",
  "firstOccurrenceAt": "2026-10-05T09:00:00-03:00",
  "occurrences": 12
}
```

Frequências suportadas:

- `DAILY`
- `WEEKLY`
- `MONTHLY`

## Invariantes

- a criação da regra é idempotente;
- uma ocorrência lógica produz no máximo um pagamento;
- múltiplas instâncias do materializador não podem avançar a mesma ocorrência duas vezes;
- a recorrência não publica Kafka diretamente;
- cada ocorrência vira um pagamento `SCHEDULED` e reutiliza o executor da Sprint 13;
- valores monetários usam `BigDecimal`;
- a regra é finita no v1.

## Concorrência

Cada materializador pode ler a mesma regra vencida, mas apenas um consegue avançá-la:

```sql
UPDATE recurring_pix_schedules
SET next_occurrence_at = :next,
    remaining_occurrences = :remaining,
    status = :status
WHERE id = :id
  AND status = 'ACTIVE'
  AND next_occurrence_at = :expected
  AND next_occurrence_at <= :now;
```

`rowsUpdated == 1` concede ownership da ocorrência.

A atualização da regra e a criação do pagamento acontecem na mesma transação local. Se a criação falhar, o avanço também é revertido.

## Idempotência da ocorrência

Cada ocorrência recebe chave determinística:

```text
recurring:<scheduleId>:<occurrenceEpochMillis>
```

Além do claim da regra, a unique constraint de `payments.idempotency_key` funciona como segunda barreira contra duplicação.

## Persistência

Nova tabela `recurring_pix_schedules`:

- `id`
- `idempotency_key`
- dados do PIX
- `frequency`
- `status`
- `next_occurrence_at`
- `remaining_occurrences`
- `anchor_day`
- timestamps

`payments` recebe:

- `recurring_schedule_id`
- `recurring_occurrence_at`

## Semântica mensal

`anchor_day` preserva o dia original da primeira ocorrência.

Exemplo para dia 31:

```text
31 Jan -> 28/29 Fev -> 31 Mar -> 30 Abr
```

Quando o mês não possui o dia âncora, usa-se o último dia daquele mês.

## Observabilidade

Métricas:

- `nexapay.payment.recurring.created`
- `nexapay.payment.recurring.materialized`
- `nexapay.payment.recurring.claim.skipped`
- `nexapay.payment.recurring.completed`

## Critérios de aceite

- [ ] endpoint protegido por `PAYMENT_CREATE`;
- [ ] primeira ocorrência deve estar no futuro;
- [ ] ocorrências entre 1 e 365;
- [ ] criação da regra é idempotente;
- [ ] scheduler encontra regras vencidas;
- [ ] claim concorrente possui winner/loser;
- [ ] winner materializa exatamente um `Payment SCHEDULED`;
- [ ] pagamento referencia regra e occurrence timestamp;
- [ ] regra avança corretamente DAILY/WEEKLY/MONTHLY;
- [ ] última ocorrência marca regra como `COMPLETED`;
- [ ] recorrência não cria Outbox diretamente;
- [ ] testes com PostgreSQL/Testcontainers validam lifecycle;
- [ ] CI permanece verde.
