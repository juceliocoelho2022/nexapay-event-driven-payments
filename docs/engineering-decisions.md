# Engineering Decisions — NexaPay

Este documento registra as principais decisões de engenharia do NexaPay, seus trade-offs e a forma de validar e operar a solução.

## 1. Problema de engenharia

O NexaPay simula uma plataforma de pagamentos distribuída. Nesse domínio, uma requisição pode atravessar vários componentes e sofrer falhas parciais, duplicidade, indisponibilidade temporária, timeout ou reprocessamento.

Requisitos que guiaram a arquitetura:
- evitar perda silenciosa de eventos;
- impedir efeitos duplicados em operações sensíveis;
- desacoplar etapas assíncronas;
- permitir rastreamento ponta a ponta;
- tornar falhas observáveis e reprocessáveis.

## 2. Microservices + Event-Driven Architecture

**Decisão:** separar pagamentos, contas, ledger, fraude e autenticação em responsabilidades distintas e usar Kafka para etapas assíncronas.

**Alternativa considerada:** monólito modular.

**Trade-off:** a arquitetura distribuída aumenta custo operacional, observabilidade, contratos de integração e tratamento de falhas. Em um produto menor, eu começaria com monólito modular e só extrairia serviços com evidência de escala, isolamento ou autonomia de times.

## 3. Transactional Outbox

**Problema:** gravar no banco e publicar no Kafka em operações independentes cria risco de dual write.

**Decisão:** persistir alteração de domínio e evento na mesma transação local, com publicação posterior pela Outbox.

**Trade-off:** exige relay, estados de publicação, retry e monitoramento, em troca de maior consistência entre banco e mensageria.

## 4. At-least-once + Idempotency

**Decisão:** assumir entrega at-least-once e tornar consumidores/operações idempotentes.

O projeto não depende de uma promessa de exactly-once global. Retries e redeliveries fazem parte do modelo de falha.

## 5. Retry, DLT e reprocessamento

Falhas transitórias podem ser retentadas. Falhas que excedem a política normal devem ser isoladas em DLT para investigação e replay controlado.

Retry precisa ter limite, backoff e métricas; ele não deve esconder erro permanente.

## 6. Observability by design

Prometheus/Grafana, Loki e OpenTelemetry/Tempo são usados para responder perguntas operacionais:
- onde a latência aumentou?
- qual serviço está falhando?
- existe backlog no processamento?
- uma transação passou por quais serviços?
- retries estão recuperando falhas ou mascarando um problema?

## 7. Estratégia de testes

A validação prioriza risco:
- testes unitários para regras;
- testes de API para contratos HTTP;
- integração para persistência e infraestrutura;
- cenários de idempotência e replay;
- cenários de falha parcial;
- CI para impedir regressões.

## 8. Como eu investigaria um pagamento pendente

1. Confirmar health dos serviços.
2. Buscar a requisição pelo correlation/trace id.
3. Verificar persistência do pagamento.
4. Verificar registro correspondente na Outbox.
5. Confirmar publicação no Kafka.
6. Verificar consumidor downstream e lag.
7. Inspecionar DLT.
8. Identificar se a causa é código, configuração, infraestrutura ou dado.
9. Corrigir e executar replay seguro quando necessário.

## 9. Regra de evolução

Antes de adicionar tecnologia, a pergunta é: **qual risco ou requisito ela resolve?**

Kubernetes, cache, novos serviços ou novas retries só entram quando o ganho esperado justificar a complexidade adicional.
