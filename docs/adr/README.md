# Architecture Decision Records (ADRs)

Este diretório registra decisões arquiteturais relevantes do NexaPay, incluindo contexto, alternativas, trade-offs e consequências operacionais.

| ADR | Decisão | Status |
|---|---|---|
| [ADR-001](ADR-001-event-driven-kafka.md) | Kafka e arquitetura orientada a eventos | Aceita |
| [ADR-002](ADR-002-idempotency-redis.md) | Idempotência com Redis | Aceita |
| [ADR-003](ADR-003-resilience-dlt.md) | Retry, Circuit Breaker e DLT | Aceita |
| [ADR-004](ADR-004-observability-slos.md) | Observabilidade, SLOs e alertas | Aceita |
| [ADR-005](ADR-005-spec-driven-ai-assisted-development.md) | SDD e desenvolvimento assistido por IA | Aceita |
| [ADR-006](ADR-006-scheduled-pix-atomic-claim.md) | PIX agendado com claim atômico no PostgreSQL | Aceita |
| [ADR-007](ADR-007-recurring-pix-materialization.md) | Recorrência materializa pagamentos agendados | Aceita |
| [ADR-008](ADR-008-atomic-cancellation-state-transition.md) | Cancelamento por transição atômica de estado | Aceita |
| [ADR-009](ADR-009-fraud-decision-outbox-state-machine.md) | Fraud Decision via Outbox e máquina de estados | Aceita |
| [ADR-010](ADR-010-manual-fraud-review-atomic-transition.md) | Revisão manual de fraude por transição atômica | Aceita |

ADRs tornam explícito não apenas **o que** foi implementado, mas **por que** uma solução foi escolhida e quais custos e riscos foram aceitos.
