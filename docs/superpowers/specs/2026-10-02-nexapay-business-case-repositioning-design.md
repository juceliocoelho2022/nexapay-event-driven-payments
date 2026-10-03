# NexaPay — Business-First Portfolio Repositioning Design

Date: 2026-10-02
Status: approved design
Target branch: `docs/business-driven-engineering`
Pull request: #29

## 1. Intent

Reposicionar o NexaPay como um case de engenharia de pagamentos distribuídos orientado por problema de negócio, invariantes financeiras, riscos operacionais e decisões arquiteturais verificáveis.

O objetivo não é alterar o comportamento produtivo da aplicação. A mudança é documental e de posicionamento de portfólio: um recrutador, Tech Lead ou entrevistador deve compreender primeiro o problema financeiro, as invariantes e os riscos distribuídos; somente depois a stack utilizada.

## 2. Público principal

- recrutadores de Java Backend e Software Engineering;
- Tech Leads e engenheiros backend;
- entrevistadores de sistemas distribuídos e pagamentos;
- revisores interessados em Kafka, consistência, concorrência, segurança e observabilidade.

## 3. Problema central

Pagamentos digitais estão sujeitos a retries do cliente, timeouts, redelivery, concorrência e falhas parciais entre banco, broker e serviços downstream.

O NexaPay deve comunicar claramente que seu valor técnico está em preservar invariantes financeiras nessas condições, especialmente:

- uma mesma intenção de pagamento não deve gerar duas operações financeiras;
- débito/crédito não pode ser aplicado duas vezes por reentrega de evento;
- banco e Kafka não podem depender de um dual write ingênuo;
- concorrência de saldo, agendamento, execução e cancelamento deve ser controlada;
- decisões de fraude devem produzir transições explícitas e auditáveis;
- falhas distribuídas devem ser diagnosticáveis por métricas, logs e traces.

## 4. Documento canônico do domínio

O arquivo `docs/business-case.md` será mantido como documento canônico de negócio e engenharia do NexaPay.

Ele já define BR-001..BR-010, FRs, NFRs e critérios de aceitação. A evolução deve ampliar sua rastreabilidade, não duplicar conteúdo em documentos paralelos.

### Regras canônicas

- **BR-001 — Idempotência de pagamento**
- **BR-002 — Precisão monetária**
- **BR-003 — Débito concorrente**
- **BR-004 — Publicação confiável**
- **BR-005 — Entrega at-least-once**
- **BR-006 — Decisão de fraude**
- **BR-007 — PIX agendado**
- **BR-008 — PIX recorrente**
- **BR-009 — Cancelamento concorrente**
- **BR-010 — Auditoria**

## 5. Estratégia de rastreabilidade

`docs/business-case.md` deverá incluir uma matriz que relacione, para cada regra relevante:

```text
Business Rule
  -> Functional / Non-Functional Requirement
  -> Service / Component
  -> SPEC / ADR
  -> Test / Evidence
```

Exemplos esperados:

- BR-001 -> FR-001/NFR-001 -> Payment Service -> PIX SPEC -> testes de idempotência;
- BR-003 -> FR-003/NFR-005 -> Account Service -> locking PostgreSQL -> testes concorrentes;
- BR-004 -> FR-004/NFR-002 -> Payment/Fraud/Account -> Outbox ADRs -> testes de persistência/publicação;
- BR-007 -> FR-008 -> Payment Service -> Scheduled PIX SPEC + ADR-006 -> Testcontainers de claim concorrente;
- BR-009 -> FR-010 -> Payment Service -> Cancellation SPEC + ADR-008 -> testes de corrida cancelamento x execução;
- BR-010 -> FR-010/FR-011 -> Payment Service -> Cancellation/Manual Fraud Review SPECs -> histórico/auditoria persistida.

Nenhuma evidência deve ser inventada. Quando não houver teste, SPEC, ADR ou output correspondente, a matriz deve indicar apenas o que está realmente presente no repositório.

## 6. README — nova hierarquia de informação

O topo do `README.md` será reorganizado para esta sequência:

1. **Business Problem**
2. **Financial Invariants / Failure Modes**
3. **Critical Payment Flow**
4. **Engineering Decisions & Trade-offs**
5. **Evidence / Tests / Observability**
6. **Architecture**
7. **Technical Snapshot / Stack**
8. **Sprints e documentação detalhada**

A primeira descrição substancial do projeto deve responder ao problema antes de listar Java, Kafka, PostgreSQL ou observabilidade.

### Mensagem central

> O NexaPay modela um sistema de pagamentos distribuído que precisa preservar invariantes financeiras diante de retries, redelivery, concorrência e falhas parciais.

## 7. Fluxo crítico a destacar

O fluxo principal deve ser apresentado assim:

```text
Client
  -> API Gateway
  -> Payment Service
  -> validation + authorization + Idempotency-Key
  -> PostgreSQL transaction
      -> payment state
      -> outbox_event
  -> Outbox Publisher
  -> Kafka
  -> Fraud Service
  -> FraudDecisionMade
  -> Kafka
  -> Payment Service
  -> state transition
```

Esse fluxo deve deixar explícito que:

- idempotência protege a entrada;
- Outbox trata o risco de dual write;
- Kafka desacopla processamento assíncrono;
- consumidores assumem `at-least-once` e devem ser idempotentes;
- Fraud Decision fecha o ciclo de estado do pagamento;
- não existe alegação de exactly-once global.

## 8. Engineering decisions

`docs/engineering-decisions.md` continuará como visão transversal das escolhas arquiteturais.

A evolução deve reforçar, sem duplicar ADRs já existentes:

### Microservices + Event-Driven Architecture

Problema resolvido: isolamento de responsabilidades e processamento assíncrono entre domínios de pagamentos, contas, ledger, fraude e autenticação.

Trade-off: maior custo de contratos, observabilidade, falhas parciais e operação distribuída.

### Transactional Outbox

Problema resolvido: dual write entre PostgreSQL e Kafka.

Trade-off: relay/publisher, backlog, retry e monitoramento operacional.

### PostgreSQL locking / atomic transitions

Problema resolvido: concorrência de saldo, agendamento, cancelamento, recorrência e revisão de fraude.

Trade-off: contenção, necessidade de índices adequados e testes com banco real.

### Kafka + at-least-once

Problema resolvido: desacoplamento, reprocessamento e consumidores independentes.

Trade-off: redelivery possível; idempotência permanece obrigatória.

### Security / JWT / authorities

Problema resolvido: autenticação e segregação de permissões para operações sensíveis como cancelamento e revisão manual de fraude.

### Observability

Problema resolvido: diagnosticar uma operação através de HTTP e Kafka usando correlationId, métricas, logs estruturados e distributed tracing.

## 9. Reaproveitamento de SPECs e ADRs existentes

Não devem ser criados novos ADRs apenas para repetir decisões já documentadas.

O reposicionamento deve apontar para artefatos existentes, incluindo quando aplicável:

- PIX Payment SPEC;
- Scheduled PIX SPEC + ADR-006;
- Recurring PIX SPEC + ADR-007;
- Cancellation SPEC + ADR-008;
- Fraud Decision SPEC + ADR-009;
- Manual Fraud Review SPEC + ADR-010;
- Fraud Review Queue SPEC + ADR-011;
- Fraud Review SLA SPEC + ADR-012;
- ADR-005 para SDD + AI-assisted development.

## 10. Portfolio Case Study

Será criado `docs/PORTFOLIO_CASE_STUDY.md` com três objetivos.

### 60 segundos

Explicar problema -> invariantes -> solução -> principais decisões -> evidências.

### 3–5 minutos

Explicar fluxo PIX, idempotência, Outbox, decisão de fraude, concorrência, retry/DLT, observabilidade e trade-offs.

### Interview Q&A

Cobrir pelo menos:

- Por que Kafka?
- Por que Transactional Outbox?
- Por que não exactly-once global?
- Como o sistema evita pagamento duplicado?
- Como evita débito concorrente inconsistente?
- Como scheduled PIX evita execução duplicada?
- Como cancelamento e execução competem com segurança?
- Como uma decisão de fraude altera o estado do pagamento?
- Como investigar um pagamento preso em `PENDING`?
- O que mudaria para produção em escala real?

As respostas devem separar explicitamente estado implementado de melhorias futuras.

## 11. Evidence strategy

O reposicionamento deverá referenciar evidências existentes no repositório, como:

- testes unitários e MockMvc;
- Testcontainers para concorrência e persistência em PostgreSQL real;
- idempotência de pagamento;
- consumer idempotency / replay protection;
- retry + DLT;
- SPECs e ADRs;
- CI/GitHub Actions;
- dashboards e métricas Prometheus/Grafana;
- logs estruturados via Loki/Alloy;
- distributed tracing via OpenTelemetry/Tempo;
- evidência de propagação HTTP -> Kafka -> Fraud;
- production-hardening artifacts e failure drills quando efetivamente executados.

O README não deve apresentar artefatos ainda pendentes como evidência concluída. Em particular, o pacote final de runtime evidence da Sprint 12 permanece incompleto enquanto o próprio repositório o marcar como pendente.

## 12. Current vs. Future

O reposicionamento deve diferenciar capacidades implementadas de evolução futura.

### Implementado / documentado

- Payment, Account, Ledger, Fraud, Auth e API Gateway;
- PIX imediato, agendado e recorrente;
- cancelamento e auditoria;
- Fraud Decision state machine;
- manual fraud review;
- fraud review queue;
- parte implementada da política de SLA conforme documentação atual;
- Outbox, Kafka, retry, DLT e idempotência;
- JWT/authorities;
- observabilidade com métricas, logs e traces.

### Em evolução / não reivindicar como concluído

- Production Hardening completo enquanto houver runtime evidence pendente;
- Sprint 19 enquanto o README permanecer com status `em evolução`;
- metas de escala, throughput, latência ou disponibilidade não medidas;
- qualquer arquitetura cloud/Kubernetes futura sem evidência correspondente.

## 13. Non-goals desta mudança

Esta evolução não deverá:

- alterar código Java;
- alterar schema/migrations;
- alterar tópicos Kafka;
- alterar contratos HTTP ou eventos;
- alterar Docker Compose;
- adicionar dependências;
- mudar regras financeiras;
- reescrever SPECs/ADRs existentes sem necessidade;
- criar métricas ou resultados fictícios.

## 14. Success criteria

Após a mudança, um revisor deve conseguir responder rapidamente:

1. Qual problema financeiro o NexaPay resolve?
2. Quais invariantes financeiras são protegidas?
3. Como retries e redelivery são tratados?
4. Como o dual write banco + Kafka é tratado?
5. Como concorrência é tratada em saldo, agendamento e cancelamento?
6. Como fraude influencia o lifecycle do pagamento?
7. Por que cada tecnologia existe?
8. Quais testes/SPECs/ADRs/evidências sustentam cada afirmação?

Se essas respostas estiverem claras antes da lista de tecnologias, o reposicionamento estará bem-sucedido.
