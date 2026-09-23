# SPEC — Fraud Review SLA & Escalation v1

**Status:** Sprint 19

## Objetivo

Adicionar priorização operacional, deadline de atendimento, escalonamento e observabilidade à Fraud Review Queue.

## Política de prioridade

| Prioridade | Critério | SLA |
|---|---|---|
| P1 | riskScore >= 85 ou amount >= 9000 | 15 min |
| P2 | riskScore >= 80 ou amount >= 7500 | 30 min |
| P3 | demais casos REVIEW | 60 min |

A prioridade é calculada quando o caso é aberto e permanece imutável durante o lifecycle do caso.

## Persistência

Adicionar a `fraud_review_cases`:

- `priority`
- `sla_due_at`
- `escalated_at`

Casos existentes recebem backfill a partir de `payments.amount`, `fraud_risk_score` e `opened_at`.

## Escalonamento

Um monitor periódico marca:

```text
OPEN + sla_due_at <= now + escalated_at IS NULL
        -> escalated_at = now
```

O escalonamento não resolve o caso e não altera ownership.

## API

```http
GET /api/v1/fraud-review/cases
    ?priority=P1|P2|P3
    &overdue=true|false
    &available=true|false
```

Cada item inclui:

- priority;
- slaDueAt;
- overdue;
- escalatedAt;
- ageSeconds;
- remainingSlaSeconds.

## Ordenação

Casos OPEN são apresentados por:

1. P1;
2. P2;
3. P3;
4. menor `sla_due_at`;
5. menor `opened_at`.

## Observabilidade

Gauges:

- `nexapay.payment.fraud_review_sla.open_cases`
- `nexapay.payment.fraud_review_sla.overdue_cases`
- `nexapay.payment.fraud_review_sla.priority_cases{priority=P1|P2|P3}`
- `nexapay.payment.fraud_review_sla.oldest_open_age_seconds`

Counter:

- `nexapay.payment.fraud_review_sla.escalated`

## Alertas Prometheus

- warning: existe caso overdue por 2 minutos;
- critical: existe P1 overdue;
- warning: backlog total OPEN acima de 20 por 5 minutos.

## Critérios de aceite

- [ ] novo caso recebe prioridade e deadline;
- [ ] backfill calcula prioridade/deadline para casos existentes;
- [ ] listagem é ordenada por criticidade e deadline;
- [ ] filtros priority/overdue/available funcionam;
- [ ] monitor marca overdue como escalado;
- [ ] resolução antes do deadline não é escalada;
- [ ] métricas refletem estado atual da fila;
- [ ] regras Prometheus são válidas via promtool;
- [ ] Testcontainers valida prioridade, filtro e escalonamento;
- [ ] CI permanece verde.
