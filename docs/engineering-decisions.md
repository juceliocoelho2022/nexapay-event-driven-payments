# Engineering Decisions — NexaPay

Este documento registra as principais decisões de engenharia do NexaPay, seus trade-offs e a forma de validar e operar a solução. As regras de negócio canônicas estão em [business-case.md](business-case.md).

## 1. Problema de engenharia

O NexaPay simula uma plataforma de pagamentos distribuída. Nesse domínio, uma requisição pode atravessar vários componentes e sofrer falhas parciais, duplicidade, indisponibilidade temporária, timeout, concorrência ou reprocessamento.

Requisitos que guiaram a arquitetura:

- evitar perda silenciosa de eventos;
- impedir efeitos duplicados em operações sensíveis;
- preservar consistência de saldo e de transições de estado sob concorrência;
- desacoplar etapas assíncronas;
- aplicar autorização explícita em operações sensíveis;
- permitir rastreamento ponta a ponta;
- tornar falhas observáveis e reprocessáveis.

## 2. Microservices + Event-Driven Architecture

**Regras relacionadas:** BR-004, BR-005, BR-006.

**Problema:** pagamentos, contas, ledger, fraude e autenticação possuem responsabilidades diferentes e algumas etapas não precisam bloquear a resposta HTTP.

**Decisão:** separar essas responsabilidades em serviços distintos e usar Kafka nas fronteiras assíncronas.

**Alternativa considerada:** monólito modular.

**Trade-off:** a arquitetura distribuída aumenta custo operacional, observabilidade, contratos de integração e tratamento de falhas. Em um produto menor, eu começaria com monólito modular e só extrairia serviços com evidência de escala, isolamento, requisitos regulatórios ou autonomia de times.

## 3. Transactional Outbox

**Regras relacionadas:** BR-004, BR-005.

**Problema:** gravar no PostgreSQL e publicar no Kafka em operações independentes cria risco de dual write. Um commit pode ser confirmado enquanto a publicação falha, ou um evento pode ser publicado para uma transação que depois não é confirmada.

**Decisão:** persistir alteração de domínio e intenção de publicação na mesma transação local, com publicação posterior pela Outbox.

**Trade-off:** exige relay/publisher, estados de publicação, retry, backlog e monitoramento operacional. Em troca, a intenção de publicar permanece durável e recuperável após o commit local.

**Limite semântico:** Outbox não cria uma transação exactly-once global. O publisher pode reenviar; consumidores continuam precisando ser idempotentes.

## 4. Kafka + at-least-once + idempotência

**Regra relacionada:** BR-005.

**Problema:** consumidores assíncronos precisam evoluir de forma independente e falhas/restarts podem causar redelivery.

**Decisão:** usar Kafka como backbone assíncrono e assumir entrega compatível com `at-least-once`, protegendo os efeitos de negócio com idempotência e replay controlado.

**Trade-off:** Kafka adiciona operação, contratos de evento, consumer groups, ordenação por partição e necessidade explícita de tratar mensagens repetidas.

O projeto não depende de uma promessa de exactly-once global. Retries e redeliveries fazem parte do modelo de falha.

Referências: [ADR-001 — Event-Driven Kafka](adr/ADR-001-event-driven-kafka.md) e [ADR-003 — Resilience/DLT](adr/ADR-003-resilience-dlt.md).

## 5. PostgreSQL locking e transições atômicas

**Regras relacionadas:** BR-003, BR-007, BR-009 e partes de BR-006/BR-010.

**Problema:** duas operações concorrentes podem tentar consumir o mesmo saldo, executar o mesmo pagamento agendado, cancelar enquanto o scheduler executa ou revisar o mesmo caso simultaneamente.

**Decisão:** usar o PostgreSQL como árbitro das transições críticas, aplicando transações, locking pessimista quando apropriado, conditional updates e claims atômicos conforme a feature.

Exemplos documentados:

- Account Service usa `PESSIMISTIC_WRITE` para proteger atualizações concorrentes de saldo;
- Scheduled PIX usa claim atômico `SCHEDULED -> PENDING`;
- cancelamento compete com execução por transição condicional;
- manual fraud review e review queue usam transições/claims atômicos para ownership e decisão.

**Trade-off:** locking e conditional updates podem gerar contenção e exigem índices, transações curtas e testes reais de concorrência. Por isso as features críticas usam Testcontainers/PostgreSQL quando o comportamento depende do banco.

Referências: [ADR-006 — Scheduled PIX Atomic Claim](adr/ADR-006-scheduled-pix-atomic-claim.md), [ADR-008 — Atomic Cancellation](adr/ADR-008-atomic-cancellation-state-transition.md), [ADR-010 — Manual Fraud Review](adr/ADR-010-manual-fraud-review-atomic-transition.md) e [ADR-011 — Fraud Review Case Lease](adr/ADR-011-fraud-review-case-lease.md).

## 6. Retry, DLT e reprocessamento

**Regras relacionadas:** BR-004, BR-005.

Falhas transitórias podem ser retentadas. Falhas que excedem a política normal devem ser isoladas em DLT para investigação e replay controlado.

Retry precisa ter limite, backoff e métricas; ele não deve esconder erro permanente. Reprocessamento precisa respeitar a idempotência do consumidor para não repetir o efeito financeiro.

Referência: [ADR-003 — Resilience/DLT](adr/ADR-003-resilience-dlt.md).

## 7. Security — JWT, roles e authorities

**Regras relacionadas:** BR-010 e operações protegidas associadas a BR-006/BR-009.

**Problema:** cancelamento, consulta protegida e revisão manual de fraude não devem estar disponíveis apenas porque um cliente conhece o endpoint.

**Decisão:** usar Spring Security, JWT e authorities para autenticar o principal e separar permissões por operação. O contexto autenticado também fornece o ator utilizado em trilhas de auditoria quando a feature exige essa informação.

Exemplos atuais:

- `PAYMENT_CANCEL` protege cancelamento;
- `PAYMENT_READ` protege históricos e consultas sensíveis;
- `FRAUD_REVIEW` protege decisão manual de fraude;
- o bootstrap atual de revisão manual concede `FRAUD_REVIEW` ao papel documentado no projeto.

**Trade-off:** segurança distribuída exige configuração consistente de Resource Server, propagação correta de identidade quando aplicável e testes de autorização; adicionar roles/authorities sem governança pode aumentar complexidade e risco de configuração incorreta.

## 8. Observability by design

**Risco relacionado:** diagnóstico de falhas que atravessam HTTP, Outbox, Kafka e consumidores.

Prometheus/Grafana, Loki/Alloy e OpenTelemetry/Tempo são usados para responder perguntas operacionais:

- onde a latência aumentou?
- qual serviço está falhando?
- existe backlog no processamento ou na Outbox?
- uma transação passou por quais serviços?
- retries estão recuperando falhas ou mascarando um problema?
- um evento saiu do Payment Service e chegou ao Fraud Service com o mesmo contexto de correlação?

**Trade-off:** instrumentação, armazenamento de telemetria e dashboards têm custo operacional. O projeto privilegia sinais que ajudam a diagnosticar fluxos financeiros e assíncronos, em vez de coletar telemetria sem pergunta operacional associada.

Referências: [ADR-004 — Observability/SLOs](adr/ADR-004-observability-slos.md), [Sprint 11 — Distributed Observability](SPRINT11-DISTRIBUTED-OBSERVABILITY.md) e [Production Hardening](production-hardening.md).

## 9. Estratégia de testes orientada a risco

A validação prioriza o tipo de falha que cada regra pode sofrer:

- testes unitários para regras determinísticas;
- MockMvc/API tests para contratos HTTP e autorização;
- testes de integração para persistência e infraestrutura;
- Testcontainers/PostgreSQL para concorrência e transições dependentes do banco;
- cenários de idempotência e replay;
- cenários de falha parcial e DLT;
- CI para impedir regressões.

A existência de Testcontainers é especialmente importante onde um mock de repository não consegue demonstrar locking, disputa concorrente ou atomicidade do banco real.

## 10. Como eu investigaria um pagamento pendente

1. Confirmar health dos serviços envolvidos.
2. Buscar a operação pelo `correlationId`/Trace ID.
3. Verificar o estado persistido no Payment Service.
4. Verificar o registro correspondente na Outbox.
5. Confirmar se a publicação Kafka ocorreu.
6. Verificar o consumidor downstream e eventual lag/retry.
7. Inspecionar DLT quando a política do fluxo a utiliza.
8. Verificar se existe decisão de fraude persistida ou evento `FraudDecisionMade` pendente de consumo.
9. Identificar se a causa é código, configuração, infraestrutura ou dado.
10. Corrigir a causa e executar replay seguro apenas respeitando idempotência e contratos do fluxo.

## 11. Regra de evolução

Antes de adicionar tecnologia, a pergunta é: **qual risco ou requisito ela resolve?**

Kubernetes, novos caches, novos serviços, novos brokers ou retries adicionais só entram quando o ganho esperado justificar a complexidade adicional. O mesmo princípio vale para trocar PostgreSQL, Kafka ou o serving atual: a decisão deve partir de evidência, não de preferência por stack.