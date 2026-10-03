# NexaPay — Portfolio Case Study

Este documento transforma o NexaPay em uma narrativa de portfólio para recrutadores, Tech Leads e entrevistas técnicas.

> Regras de negócio: [business-case.md](business-case.md)  
> Decisões: [engineering-decisions.md](engineering-decisions.md)  
> Production hardening: [production-hardening.md](production-hardening.md)

## 1. Explicação de 60 segundos

O **NexaPay** é um case de Backend Java para um problema típico de pagamentos distribuídos: a mesma intenção financeira pode chegar mais de uma vez por retry, enquanto banco, broker e consumidores também podem falhar de forma independente.

A principal preocupação é preservar invariantes financeiras. O Payment Service aplica `Idempotency-Key` para impedir que um retry do cliente crie outro pagamento lógico. O PostgreSQL persiste o estado financeiro, e a Transactional Outbox registra a intenção de publicação na mesma transação local para reduzir o risco de dual write com Kafka.

Kafka desacopla o processamento assíncrono. Como o fluxo assume semântica compatível com `at-least-once`, os consumidores precisam tolerar redelivery sem repetir efeitos. O Fraud Service classifica o pagamento e publica uma decisão que volta ao Payment Service, fechando a state machine.

Concorrência crítica — como saldo, execução agendada, cancelamento e revisão de fraude — é tratada com transações, locking e transições atômicas no PostgreSQL. Testcontainers é usado nas features em que mocks não demonstrariam o comportamento real do banco.

O case também inclui métricas, logs estruturados e distributed tracing para investigar uma operação através de HTTP, Outbox e Kafka. O objetivo é mostrar como cada tecnologia existe para preservar uma regra, reduzir um risco ou tornar uma falha diagnosticável.

## 2. Explicação técnica de 3–5 minutos

### 2.1 Problema do domínio

Pagamentos não podem tratar retries e redelivery como casos excepcionais. Em um sistema distribuído, eles fazem parte do modelo normal de falha.

Os riscos centrais do NexaPay são:

- retry HTTP criar dois pagamentos;
- commit no banco sem publicação correspondente;
- redelivery Kafka repetir um efeito financeiro;
- duas operações concorrentes consumirem o mesmo saldo;
- duas instâncias executarem o mesmo PIX agendado;
- cancelamento e execução vencerem ao mesmo tempo;
- decisão de fraude se perder ou ser aplicada fora do estado correto;
- uma falha atravessar vários serviços sem rastreabilidade suficiente.

As regras canônicas estão em [business-case.md](business-case.md).

### 2.2 Criação de PIX e idempotência

O fluxo de entrada começa no API Gateway e chega ao Payment Service.

```text
Client
  ↓
API Gateway
  ↓
Payment Service
  ├── authentication / authorization
  ├── validation
  └── Idempotency-Key
  ↓
PostgreSQL transaction
  ├── payment state
  └── outbox_event
```

A `Idempotency-Key` representa uma única intenção lógica. Um retry da mesma intenção não deve criar outra transação financeira.

Referência: [PIX Payment SPEC](specs/pix-payment-v1/README.md).

### 2.3 Transactional Outbox e dual write

Persistir no PostgreSQL e publicar diretamente no Kafka são duas operações diferentes. Se forem tratadas como um dual write simples, podem divergir.

O NexaPay persiste estado de domínio e `outbox_event` na mesma transação local. Depois do commit, o publisher envia o evento ao Kafka.

```text
PostgreSQL commit
   ├── payment
   └── outbox_event
          ↓
     Outbox Publisher
          ↓
        Kafka
```

Isso cria um ponto de recuperação durável para a publicação, mas não cria exactly-once global. O publisher pode repetir a entrega em determinados limites de falha; consumidores permanecem idempotentes.

Referência: [Engineering Decisions — Transactional Outbox](engineering-decisions.md#3-transactional-outbox).

### 2.4 Kafka, at-least-once e consumers idempotentes

Kafka é usado para desacoplar processamento assíncrono e permitir que consumidores tenham responsabilidades independentes.

A arquitetura assume entrega compatível com `at-least-once`:

```text
producer
   ↓
Kafka
   ↓
consumer
   ├── success
   ├── retry
   └── DLT / controlled replay
```

Redelivery é tratado como possibilidade real. O Ledger e os fluxos de fraude não devem duplicar o efeito de um evento já processado.

Referências: [ADR-001 — Event-Driven Kafka](adr/ADR-001-event-driven-kafka.md) e [ADR-003 — Resilience/DLT](adr/ADR-003-resilience-dlt.md).

### 2.5 Fraude e state machine do pagamento

A decisão de fraude fecha um ciclo assíncrono entre Payment e Fraud:

```text
Payment PENDING
      ↓ PaymentCreated
Kafka
      ↓
Fraud Service
      ├── APPROVED
      ├── REVIEW
      └── BLOCKED
      ↓ FraudDecisionMade
Kafka
      ↓
Payment Service
      ├── APPROVED -> COMPLETED
      ├── REVIEW   -> REVIEW
      └── BLOCKED  -> REJECTED
```

A decisão e sua intenção de publicação também utilizam Outbox no Fraud Service. O Payment Service aplica a transição somente a partir do estado esperado e deduplica identificadores documentados pelo fluxo.

Referências: [Fraud Decision SPEC](specs/fraud-decision-state-machine-v1/README.md) e [ADR-009 — Fraud Decision via Outbox](adr/ADR-009-fraud-decision-outbox-state-machine.md).

### 2.6 Concorrência de saldo

O Account Service controla crédito, débito e saldo com `BigDecimal`, transações e locking no PostgreSQL.

O ponto importante não é apenas o uso de uma annotation de lock: a regra é impedir que duas operações concorrentes usem o mesmo saldo disponível de forma inconsistente.

O README documenta o uso de `PESSIMISTIC_WRITE` nesse limite.

### 2.7 PIX agendado e claim atômico

Um pagamento agendado começa em `SCHEDULED` e não deve produzir evento financeiro antes de `scheduledAt`.

Quando fica elegível, múltiplas instâncias podem disputar a execução:

```text
SCHEDULED
   ↓ eligibility
atomic claim
SCHEDULED -> PENDING
   ↓
Outbox
   ↓
Kafka
```

Somente a instância vencedora do claim cria a Outbox correspondente ao início da execução.

Referências: [Scheduled PIX SPEC](specs/scheduled-pix-v1/README.md) e [ADR-006 — Atomic Claim](adr/ADR-006-scheduled-pix-atomic-claim.md).

### 2.8 PIX recorrente

A recorrência é separada da execução financeira individual.

```text
Recurring Rule
   ↓ occurrence
atomic occurrence claim
   ↓
Payment SCHEDULED
   ↓
Scheduled PIX execution pipeline
```

Isso evita duplicar a lógica de Outbox/Kafka/fraude dentro do mecanismo de recorrência.

Referências: [Recurring PIX SPEC](specs/recurring-pix-v1/README.md) e [ADR-007 — Materialization](adr/ADR-007-recurring-pix-materialization.md).

### 2.9 Cancelamento versus execução

Cancelamento e scheduler podem competir pelo mesmo pagamento agendado.

```text
Payment SCHEDULED
   ├── cancellation claim -> CANCELLED
   └── execution claim    -> PENDING
```

A proteção vem da transição atômica/condicional no banco, não do Kafka. Apenas uma transição compatível com o estado corrente pode vencer.

Referências: [Cancellation SPEC](specs/cancellation-v1/README.md) e [ADR-008 — Atomic Cancellation](adr/ADR-008-atomic-cancellation-state-transition.md).

### 2.10 Revisão manual e fila de fraude

Quando a decisão automática retorna `REVIEW`, o fluxo pode seguir para análise humana.

A revisão manual usa authority dedicada e transição atômica. A Fraud Review Queue adiciona ownership temporário por lease para impedir que dois analistas atuem sobre o mesmo caso como donos válidos ao mesmo tempo.

Referências: [Manual Fraud Review SPEC](specs/manual-fraud-review-v1/README.md), [ADR-010](adr/ADR-010-manual-fraud-review-atomic-transition.md), [Fraud Review Queue SPEC](specs/fraud-review-queue-v1/README.md) e [ADR-011](adr/ADR-011-fraud-review-case-lease.md).

### 2.11 Retry, DLT e replay

Retry existe para falhas transitórias, não para esconder erro permanente. Quando a política de tentativas se esgota, a mensagem pode seguir para DLT conforme o contrato do fluxo.

Replay deve ser controlado e só é seguro quando a fronteira reprocessada é idempotente.

### 2.12 Observabilidade distribuída

O projeto cobre os três pilares:

```text
Metrics -> Micrometer -> Prometheus -> Grafana
Logs    -> Structured JSON -> Alloy -> Loki -> Grafana
Traces  -> OpenTelemetry / OTLP -> Tempo -> Grafana
```

O fluxo documentado de tracing acompanha uma operação entre:

```text
API Gateway
   ↓ HTTP
Payment Service
   ↓ Outbox / Kafka
Fraud Service
```

Essa visibilidade permite correlacionar requisição, persistência, publicação e consumo assíncrono.

Referências: [Sprint 11 — Distributed Observability](SPRINT11-DISTRIBUTED-OBSERVABILITY.md) e [Production Hardening](production-hardening.md).

## 3. Principais decisões e trade-offs

| Decisão | Problema resolvido | Trade-off |
|---|---|---|
| `Idempotency-Key` | retry do cliente criando outra intenção financeira | armazenamento/expiração e contrato de replay |
| PostgreSQL | fonte transacional e arbitragem de concorrência | locking pode gerar contenção |
| Transactional Outbox | dual write entre banco e Kafka | publisher, backlog, retry e monitoramento adicionais |
| Kafka | desacoplamento e processamento assíncrono | redelivery, operação e contratos de evento |
| Retry + DLT | falhas transitórias e isolamento de falhas permanentes | políticas erradas podem aumentar atraso ou esconder causa raiz |
| JWT + authorities | segregação de operações sensíveis | complexidade de configuração e autorização distribuída |
| Testcontainers | validar concorrência/atomicidade no banco real | testes mais pesados que unit tests |
| Prometheus/Loki/Tempo | diagnóstico de fluxo distribuído | custo de instrumentação e armazenamento de telemetria |

## 4. Interview Q&A

### Por que Kafka?

Porque o domínio possui etapas que não precisam completar dentro da chamada HTTP e diferentes consumidores precisam processar eventos de forma independente. Kafka também fornece retenção/replay compatíveis com o modelo de eventos do projeto.

**Trade-off:** aumenta complexidade operacional e não elimina mensagens repetidas; consumidores continuam idempotentes.

### Por que Transactional Outbox?

Para evitar o dual write ingênuo entre PostgreSQL e Kafka. O estado da operação e a intenção de publicar ficam na mesma transação local; a publicação acontece depois.

Isso melhora recuperabilidade diante de indisponibilidade do broker, mas exige um publisher e tratamento de backlog/retry.

### Por que não exactly-once global?

Porque a operação atravessa banco, publisher, broker e múltiplos consumidores. Uma garantia exatamente-uma-vez ponta a ponta exigiria uma semântica mais forte do que a arquitetura demonstra.

O NexaPay prefere assumir `at-least-once` e tornar os limites relevantes idempotentes.

### Como o sistema evita pagamento duplicado?

No limite HTTP, a mesma `Idempotency-Key` representa uma única intenção lógica de pagamento. Um retry não deve criar um segundo pagamento.

Isso é diferente da idempotência do consumer Kafka, que protege contra reentrega assíncrona.

### Como evita débito concorrente inconsistente?

A consistência do saldo é responsabilidade do Account Service/PostgreSQL. O fluxo documenta transações e `PESSIMISTIC_WRITE` para serializar a atualização crítica quando necessário.

Kafka não fornece a consistência do saldo.

### Como o PIX agendado evita execução duplicada?

Quando `scheduledAt <= now`, instâncias do scheduler disputam um claim atômico no PostgreSQL. A transição `SCHEDULED -> PENDING` só pode ser vencida por uma execução; somente o vencedor cria a Outbox.

### Como cancelamento e execução competem com segurança?

Ambos partem de um estado elegível e tentam uma transição condicional. O PostgreSQL decide atomicamente qual transição é válida com base no estado atual. Depois que uma vence, a outra não encontra mais a pré-condição necessária.

### Como a decisão de fraude altera o pagamento?

O Fraud Service consome `PaymentCreated`, calcula a decisão e publica `FraudDecisionMade` via Outbox. O Payment Service consome essa decisão e aplica a state transition permitida a partir de `PENDING`.

### Como investigar um pagamento preso em `PENDING`?

Eu seguiria a cadeia de evidência:

1. health dos serviços;
2. `correlationId` / Trace ID;
3. estado do pagamento;
4. registro da Outbox;
5. publicação Kafka;
6. processamento no Fraud Service;
7. retries/DLT;
8. presença de `FraudDecisionMade`;
9. consumo da decisão pelo Payment Service;
10. causa raiz e replay seguro, se necessário.

Esse diagnóstico usa métricas, logs e traces em vez de tentar deduzir o problema apenas pelo status HTTP inicial.

### O que mudaria para produção em escala real?

Eu começaria por evidência operacional, não por adicionar infraestrutura antecipadamente. Dependendo dos resultados, avaliaria:

- load tests e SLOs baseados em comportamento medido;
- particionamento e chaves Kafka baseados em ordering/volume reais;
- capacity planning de PostgreSQL e índices para claims/locks concorrentes;
- CDC para Outbox caso o publisher atual se torne gargalo comprovado;
- gestão de secrets e identidade de workload;
- infraestrutura cloud, autoscaling e isolamento de ambientes;
- procedimentos formais de disaster recovery;
- governança de schema/eventos e compatibilidade;
- estratégia de retenção e auditoria adequada a requisitos regulatórios reais.

Esses itens são possibilidades de evolução, não capacidades já reivindicadas pelo repositório.

## 5. Evidence Map

| Claim | Artefato verificável |
|---|---|
| PIX idempotente | [PIX Payment SPEC](specs/pix-payment-v1/README.md) + documentação do Payment Service |
| Kafka/event-driven | [ADR-001](adr/ADR-001-event-driven-kafka.md) |
| idempotência / replay | [ADR-002](adr/ADR-002-idempotency-redis.md) + estratégia de testes do projeto |
| retry + DLT | [ADR-003](adr/ADR-003-resilience-dlt.md) + README |
| observabilidade e SLOs técnicos | [ADR-004](adr/ADR-004-observability-slos.md) + [Sprint 11](SPRINT11-DISTRIBUTED-OBSERVABILITY.md) |
| SDD + AI com guardrails | [ADR-005](adr/ADR-005-spec-driven-ai-assisted-development.md) + [AGENTS.md](../AGENTS.md) |
| Scheduled PIX concorrente | [Scheduled PIX SPEC](specs/scheduled-pix-v1/README.md) + [ADR-006](adr/ADR-006-scheduled-pix-atomic-claim.md) |
| Recurring PIX | [Recurring PIX SPEC](specs/recurring-pix-v1/README.md) + [ADR-007](adr/ADR-007-recurring-pix-materialization.md) |
| cancelamento concorrente | [Cancellation SPEC](specs/cancellation-v1/README.md) + [ADR-008](adr/ADR-008-atomic-cancellation-state-transition.md) |
| decisão de fraude | [Fraud Decision SPEC](specs/fraud-decision-state-machine-v1/README.md) + [ADR-009](adr/ADR-009-fraud-decision-outbox-state-machine.md) |
| revisão manual | [Manual Fraud Review SPEC](specs/manual-fraud-review-v1/README.md) + [ADR-010](adr/ADR-010-manual-fraud-review-atomic-transition.md) |
| fila operacional/lease | [Fraud Review Queue SPEC](specs/fraud-review-queue-v1/README.md) + [ADR-011](adr/ADR-011-fraud-review-case-lease.md) |
| SLA de revisão em evolução | [Fraud Review SLA SPEC](specs/fraud-review-sla-v1/README.md) + ADR-012 documentado no README |
| production hardening | [Production Hardening](production-hardening.md); pacote final de runtime evidence continua pendente enquanto o documento/README assim indicar |

## 6. Limites atuais

Dois pontos devem permanecer explícitos ao apresentar o projeto:

- **Sprint 12 — Production Hardening**: artefatos de hardening existem, mas o pacote final de evidência de runtime permanece pendente conforme o próprio repositório;
- **Sprint 19 — Fraud Review SLA**: permanece em evolução enquanto o README mantiver esse status.

Também não devem ser inferidos throughput, disponibilidade, latência ou capacidade de produção sem benchmark correspondente.

## 7. Frase de fechamento para entrevista

> O NexaPay não foi construído para mostrar uma lista de tecnologias. Cada componente protege uma invariável ou trata um modo de falha: idempotência evita duplicação, PostgreSQL arbitra concorrência, Outbox trata dual write, Kafka desacopla o processamento, consumers idempotentes tornam redelivery seguro, e observabilidade permite reconstruir o caminho de uma operação distribuída. O valor do projeto está em conectar regra financeira, decisão arquitetural e evidência.