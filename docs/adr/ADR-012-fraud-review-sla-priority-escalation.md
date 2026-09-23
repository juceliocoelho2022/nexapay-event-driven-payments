# ADR-012 — SLA de revisão de fraude com prioridade persistida

**Status:** Aceita

## Contexto

A Sprint 18 introduziu ownership com lease, mas a fila ainda não diferencia criticidade nem mede atraso operacional.

## Decisão

Persistir `priority` e `sla_due_at` no momento de abertura do caso.

### Política

```text
P1 -> riskScore >= 85 OR amount >= 9000 -> 15 min
P2 -> riskScore >= 80 OR amount >= 7500 -> 30 min
P3 -> otherwise                         -> 60 min
```

A política fica encapsulada em `FraudReviewSlaPolicy`.

## Por que persistir

Não recalcular prioridade em cada leitura.

Persistência garante:

- histórico determinístico;
- ordenação estável;
- capacidade de auditoria;
- consultas e alertas simples;
- evolução futura da política sem reclassificar casos antigos silenciosamente.

## Escalonamento

Um scheduler periódico executa conditional update para casos OPEN vencidos ainda não escalados.

`escalated_at` é evidência operacional; não é um novo estado de domínio.

## Métricas

Um componente de snapshot atualiza gauges periodicamente a partir do PostgreSQL.

Isso evita consulta ao banco em cada scrape Prometheus.

## Alertas

Prometheus usa gauges do Payment Service para:

- overdue geral;
- P1 overdue;
- backlog elevado.

## Alternativas

### Prioridade calculada somente na API

Rejeitada por falta de estabilidade histórica.

### Redis sorted set

Não adotado: o volume atual não justifica duplicar estado da fila.

### Broker dedicado de work queue

Pode ser considerado se houver alto volume, roteamento por skills ou múltiplos grupos de analistas.

## Consequências

- nova migration e scheduler;
- SLA passa a ser parte observável do produto;
- política precisa ser versionada quando mudar.
