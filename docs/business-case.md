# NexaPay — Business Case and Domain Rules

## 1. Problema de negócio

Pagamentos digitais são operações sensíveis a duplicidade, concorrência, falhas parciais e indisponibilidade temporária de dependências. Um cliente pode reenviar uma requisição após timeout sem saber se o pagamento anterior foi aceito. Serviços internos também podem receber o mesmo evento mais de uma vez.

O NexaPay modela esse cenário como uma plataforma distribuída de pagamentos PIX cujo objetivo não é apenas processar uma requisição HTTP, mas preservar invariantes financeiras mesmo quando a infraestrutura apresenta retries, redelivery e falhas transitórias.

## 2. Riscos que o sistema precisa controlar

- execução duplicada do mesmo pagamento;
- débito ou crédito repetido por redelivery de evento;
- divergência entre banco de dados e Kafka por dual-write;
- perda de rastreabilidade entre Payment, Account, Ledger e Fraud;
- concorrência entre execução, agendamento e cancelamento;
- processamento financeiro sem decisão de fraude consistente;
- falhas operacionais sem métricas, logs ou traces suficientes para diagnóstico.

## 3. Objetivos do produto

- garantir que uma mesma intenção de pagamento não gere duas operações financeiras;
- manter trilha auditável das transições relevantes;
- desacoplar fluxos que não precisam bloquear a resposta HTTP;
- permitir reprocessamento seguro quando eventos forem entregues novamente;
- tornar falhas operacionais observáveis;
- manter regras financeiras explícitas e cobertas por testes automatizados.

## 4. Atores

- **Cliente**: inicia um pagamento PIX.
- **Payment Service**: controla o lifecycle do pagamento.
- **Account Service**: controla saldo, crédito e débito.
- **Ledger Service**: mantém histórico financeiro derivado dos eventos.
- **Fraud Service**: classifica risco e suporta revisão manual.
- **Auth Service**: autentica usuários e emite JWT.
- **API Gateway**: centraliza entrada e roteamento.
- **Operador de fraude**: assume, revisa e libera casos que exigem análise humana.

## 5. Regras de negócio

### BR-001 — Idempotência de pagamento

Uma mesma `Idempotency-Key` representa uma única intenção de pagamento.

Reenvios da mesma intenção não podem criar uma segunda transação financeira.

### BR-002 — Precisão monetária

Valores monetários devem usar representação decimal apropriada ao domínio financeiro e nunca depender de ponto flutuante binário para cálculo de saldo.

### BR-003 — Débito concorrente

A atualização de saldo precisa impedir que duas operações concorrentes consumam o mesmo saldo disponível de forma inconsistente.

### BR-004 — Publicação confiável

Uma mudança confirmada no banco e seu evento de domínio devem ser registrados atomicamente por Transactional Outbox. A publicação Kafka ocorre posteriormente.

### BR-005 — Entrega at-least-once

Consumidores Kafka devem assumir que um evento pode ser entregue mais de uma vez. Efeitos de negócio devem ser idempotentes.

### BR-006 — Decisão de fraude

O processamento de risco deve produzir um estado explícito (`APPROVED`, `REVIEW` ou `BLOCKED`) e permitir rastrear a decisão associada ao pagamento.

### BR-007 — PIX agendado

Um pagamento agendado não pode ser executado antes de `scheduledAt`. Quando elegível, múltiplas instâncias do scheduler devem competir por um claim atômico para que apenas uma materialize a execução.

### BR-008 — PIX recorrente

Uma regra de recorrência define quando pagamentos serão materializados; ela não deve duplicar a lógica de execução financeira do pagamento individual.

### BR-009 — Cancelamento concorrente

Cancelamento e execução podem competir pelo mesmo pagamento. A transição válida deve ser decidida de forma atômica no banco.

### BR-010 — Auditoria

Eventos administrativos sensíveis, como cancelamento e revisão de fraude, devem preservar ator, instante, motivo e contexto suficiente para reconstrução posterior.

## 6. Requisitos funcionais principais

- **FR-001** Criar pagamento PIX com idempotência.
- **FR-002** Consultar pagamento por identificador.
- **FR-003** Criar conta, creditar e debitar saldo.
- **FR-004** Publicar eventos financeiros por Outbox/Kafka.
- **FR-005** Registrar lançamentos no Ledger de forma idempotente.
- **FR-006** Processar decisão de fraude assincronamente.
- **FR-007** Autenticar usuários e aplicar permissões.
- **FR-008** Criar e executar PIX agendado.
- **FR-009** Criar e materializar PIX recorrente.
- **FR-010** Cancelar operações elegíveis com trilha de auditoria.
- **FR-011** Permitir revisão manual de fraude e fila operacional.

## 7. Requisitos não funcionais

- **NFR-001 — Consistência:** operações financeiras não podem depender de exactly-once global.
- **NFR-002 — Resiliência:** falhas temporárias devem ser tratadas com retry controlado, DLT e reprocessamento seguro.
- **NFR-003 — Observabilidade:** requisições e eventos devem carregar correlação suficiente para tracing ponta a ponta.
- **NFR-004 — Segurança:** endpoints protegidos devem validar JWT e authorities.
- **NFR-005 — Testabilidade:** regras de concorrência, idempotência e persistência devem possuir testes automatizados, incluindo banco real quando necessário.
- **NFR-006 — Evolução:** serviços devem manter contratos explícitos de API e evento.

## 8. Matriz de rastreabilidade

A tabela abaixo conecta regra de negócio, requisito, componente e artefato verificável. Quando não existe um ADR dedicado, a evidência aponta para o README ou para a SPEC que documenta o comportamento.

| Regra | Requisitos relacionados | Serviço / componente | SPEC / ADR | Evidência disponível |
|---|---|---|---|---|
| **BR-001 — Idempotência de pagamento** | FR-001, NFR-001, NFR-005 | Payment Service | [PIX Payment SPEC](specs/pix-payment-v1/README.md), [ADR-002 — Idempotency](adr/ADR-002-idempotency-redis.md) | README descreve `Idempotency-Key`; testes automatizados de idempotência fazem parte da estratégia do projeto |
| **BR-002 — Precisão monetária** | FR-003, NFR-005 | Account Service | README — Account Service | Uso documentado de `BigDecimal` para saldo, crédito e débito |
| **BR-003 — Débito concorrente** | FR-003, NFR-005 | Account Service / PostgreSQL | README — Account Service | `PESSIMISTIC_WRITE`, transações e validação com banco real quando aplicável |
| **BR-004 — Publicação confiável** | FR-004, NFR-002 | Payment, Account e Fraud Services | [Engineering Decisions](engineering-decisions.md), [ADR-009 — Fraud Decision via Outbox](adr/ADR-009-fraud-decision-outbox-state-machine.md) | Transactional Outbox documentada no fluxo de pagamento e fraude |
| **BR-005 — Entrega at-least-once** | FR-004, FR-005, FR-006, NFR-001, NFR-002 | Kafka, Ledger e Fraud consumers | [ADR-001 — Event-Driven Kafka](adr/ADR-001-event-driven-kafka.md), [ADR-003 — Resilience/DLT](adr/ADR-003-resilience-dlt.md) | README documenta consumer idempotente, retry, DLT e replay protection |
| **BR-006 — Decisão de fraude** | FR-006, FR-011, NFR-003, NFR-004 | Fraud Service + Payment Service | [Fraud Decision SPEC](specs/fraud-decision-state-machine-v1/README.md), [ADR-009](adr/ADR-009-fraud-decision-outbox-state-machine.md), [Manual Review SPEC](specs/manual-fraud-review-v1/README.md), [ADR-010](adr/ADR-010-manual-fraud-review-atomic-transition.md) | State machine e revisão manual documentadas com concorrência validada em PostgreSQL real |
| **BR-007 — PIX agendado** | FR-008, NFR-005 | Payment scheduler + PostgreSQL | [Scheduled PIX SPEC](specs/scheduled-pix-v1/README.md), [ADR-006 — Atomic Claim](adr/ADR-006-scheduled-pix-atomic-claim.md) | README registra claim atômico, ausência de publicação antecipada e testes com múltiplas instâncias |
| **BR-008 — PIX recorrente** | FR-009, NFR-005 | Payment recurring scheduler | [Recurring PIX SPEC](specs/recurring-pix-v1/README.md), [ADR-007 — Materialization](adr/ADR-007-recurring-pix-materialization.md) | Chave determinística por ocorrência, claim concorrente e Testcontainers descritos no README |
| **BR-009 — Cancelamento concorrente** | FR-010, NFR-005 | Payment Service + PostgreSQL | [Cancellation SPEC](specs/cancellation-v1/README.md), [ADR-008 — Atomic Cancellation](adr/ADR-008-atomic-cancellation-state-transition.md) | Corrida cancelamento x execução e conditional update descritos e validados com Testcontainers |
| **BR-010 — Auditoria** | FR-010, FR-011, NFR-003, NFR-004 | Payment Service / Fraud Review | [Cancellation SPEC](specs/cancellation-v1/README.md), [Manual Review SPEC](specs/manual-fraud-review-v1/README.md), [Fraud Review Queue SPEC](specs/fraud-review-queue-v1/README.md) | Auditoria imutável com ator, motivo, instante e histórico protegido documentados no README |

A matriz não implica que todos os comportamentos possuam o mesmo tipo de evidência. Algumas capacidades são demonstradas por SPEC + ADR + Testcontainers; outras são sustentadas pela implementação e pela documentação operacional existente.

## 9. Fluxo crítico — criação de pagamento PIX

```text
Cliente
   |
   v
API Gateway
   |
   v
Payment Service
   |
   +--> autenticação/autorização
   +--> validação
   +--> Idempotency-Key
   |
   v
PostgreSQL transaction
   |-- payment
   `-- outbox_event
          |
          v
     Outbox Publisher
          |
          v
        Kafka
          |
          v
     Fraud Service
          |
          v
   decisão de risco
```

## 10. Critérios de aceitação

### AC-001 — Retry do cliente

**Given** que um pagamento já foi criado com uma `Idempotency-Key`  
**When** a mesma intenção é enviada novamente  
**Then** nenhum segundo pagamento é criado  
**And** a operação original é reutilizada.

### AC-002 — Falha do broker

**Given** que o pagamento e a Outbox foram confirmados no PostgreSQL  
**And** Kafka está indisponível  
**When** a publicação falha  
**Then** o pagamento permanece válido no banco  
**And** o evento pode ser publicado posteriormente a partir da Outbox.

### AC-003 — Redelivery Kafka

**Given** que um consumidor já processou determinado evento  
**When** Kafka entrega o mesmo evento novamente  
**Then** nenhum efeito financeiro duplicado é produzido.

### AC-004 — Concorrência de saldo

**Given** duas tentativas concorrentes de débito sobre a mesma conta  
**When** ambas competem pelo saldo  
**Then** a estratégia de locking preserva a consistência definida pelo domínio.

### AC-005 — Cancelamento versus execução

**Given** um pagamento agendado elegível para execução  
**When** cancelamento e scheduler competem simultaneamente  
**Then** apenas uma transição terminal permitida é aplicada.

## 11. Por que as tecnologias existem

| Tecnologia / padrão | Problema resolvido |
|---|---|
| Java 21 + Spring Boot | implementação dos serviços e contratos HTTP |
| PostgreSQL | fonte transacional de verdade e controle concorrente |
| Kafka | desacoplamento e processamento assíncrono de eventos |
| Transactional Outbox | evita dual-write ingênuo banco + broker |
| Idempotency-Key | protege contra retry duplicado do cliente |
| Redis | suporte a casos de baixa latência quando aplicável |
| Retry + DLT | trata falhas transitórias sem loop infinito |
| JWT / Spring Security | autenticação e autorização entre clientes e APIs |
| OpenTelemetry / Tempo | rastreabilidade distribuída |
| Prometheus / Grafana | métricas, SLOs e diagnóstico operacional |
| Loki / Alloy | logs estruturados e investigação correlacionada |
| Testcontainers | validação de comportamento com dependências reais |

## 12. Métricas de negócio e operação

- pagamentos criados;
- replays bloqueados por idempotência;
- pagamentos aprovados, em revisão e bloqueados;
- backlog da Outbox;
- mensagens em retry e DLT;
- taxa de falhas HTTP;
- latência por serviço;
- casos de fraude em fila e fora do SLA;
- cancelamentos concluídos e rejeitados;
- divergências detectadas entre fluxo financeiro e Ledger.

## 13. Estado de evidência e limites

Este documento separa **comportamento implementado/documentado** de evidência operacional ainda pendente.

- a Sprint 12 possui runbook, SLI/SLO targets, regras Prometheus e failure-drill procedures, mas o próprio repositório mantém pendente o pacote final de runtime evidence;
- a Sprint 19 permanece marcada como **em evolução** enquanto o README mantiver esse status;
- números de throughput, disponibilidade, latência ou escala de produção não devem ser inferidos sem benchmark ou evidência operacional correspondente;
- o projeto não reivindica uma transação exactly-once global entre PostgreSQL, Kafka e todos os consumidores.

## 14. Como apresentar o projeto em entrevista

> O NexaPay modela um problema central de pagamentos distribuídos: uma mesma intenção pode chegar mais de uma vez por retry, enquanto banco e mensageria também podem falhar separadamente. Por isso a arquitetura usa idempotência na entrada, PostgreSQL como fonte transacional, Transactional Outbox para publicação confiável, Kafka com consumidores idempotentes e observabilidade ponta a ponta. O objetivo é preservar invariantes financeiras mesmo sob redelivery, concorrência e falhas parciais.

Essa narrativa deve vir antes da lista de tecnologias: primeiro o problema e as invariantes; depois as escolhas técnicas que as sustentam.