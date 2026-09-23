# ADR-007 — Recorrência materializa pagamentos agendados

**Status:** Aceita

## Contexto

A Sprint 13 já possui um mecanismo robusto para executar pagamentos futuros: estado `SCHEDULED`, claim atômico e Transactional Outbox.

A Sprint 14 precisa adicionar recorrência sem duplicar esse pipeline.

## Decisão

Separar dois conceitos:

1. **Recurring Schedule** — regra que calcula a próxima ocorrência.
2. **Scheduled Payment** — unidade financeira executável.

Quando uma regra vence, o materializador avança a regra com conditional update e cria um pagamento `SCHEDULED` na mesma transação.

O materializador não publica Kafka e não cria Outbox.

## Concorrência

Ownership da ocorrência é obtido por conditional update no PostgreSQL usando `id + next_occurrence_at`.

A ocorrência também usa uma `Idempotency-Key` determinística no pagamento como segunda barreira de segurança.

## Por que não executar diretamente a recorrência

Executar a regra diretamente criaria dois caminhos de pagamento:

- one-shot da Sprint 13;
- recorrente da Sprint 14.

Isso duplicaria regras de idempotência, observabilidade, execução e Outbox.

A materialização mantém um único pipeline.

## Por que não Quartz no v1

O requisito atual é recorrência simples e finita. PostgreSQL + Spring Scheduling já fazem parte do sistema.

Quartz ou scheduler externo só serão avaliados se surgirem requisitos como calendários complexos, misfire policies, administração de jobs ou escala que justifique a dependência.

## Consequências

### Positivas

- reutilização integral da Sprint 13;
- uma única semântica de execução financeira;
- concorrência testável;
- rastreabilidade entre regra e pagamento;
- menor superfície operacional.

### Custos

- polling;
- tabela adicional;
- cálculo explícito de calendário;
- duas etapas temporais: materialização e execução.

## Evolução

Medir lag de materialização e volume de regras antes de adotar mecanismos mais complexos.
