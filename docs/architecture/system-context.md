# NexaPay — System Context

## Objetivo

Mapa compacto das fronteiras principais para orientar análise de impacto, revisão arquitetural e agentes de IA.

## Serviços

| Componente | Responsabilidade |
|---|---|
| API Gateway | entrada, roteamento e segurança de borda |
| Auth Service | autenticação, JWT, roles e permissions |
| Payment Service | criação e consulta de pagamentos PIX |
| Account Service | contas, crédito, débito e saldo |
| Ledger Service | histórico financeiro orientado a eventos |
| Fraud Service | análise assíncrona de risco |
| PostgreSQL | persistência por contexto/serviço |
| Kafka | integração assíncrona |
| Observability stack | métricas, logs e traces |

## Fluxo PIX observado

```text
Client
  |
  v
API Gateway
  |
  v
Payment Service
  |
  +--> PostgreSQL
  |
  +--> Transactional Outbox
          |
          v
        Kafka
          |
          v
     Fraud Service
```

## Fluxo de conta/ledger

```text
Account Service
      |
      +--> PostgreSQL
      |
      +--> Outbox/Kafka
               |
               v
          Ledger Service
```

## Fronteiras de consistência

Não há transação ACID global entre serviços.

Consistência local é protegida pelo banco de cada contexto e, em fluxos assíncronos relevantes, pelo Transactional Outbox. A integração Kafka deve assumir at-least-once e consumidores idempotentes.

## Fronteiras de observabilidade

```text
Metrics -> Micrometer -> Prometheus -> Grafana
Logs    -> structured JSON -> Alloy -> Loki -> Grafana
Traces  -> OpenTelemetry/OTLP -> Tempo -> Grafana
```

Análises de incidente devem priorizar correlação entre traceId/correlationId, logs e métricas em vez de diagnosticar cada serviço isoladamente.

## Regra para novas dependências

Uma nova infraestrutura, serviço ou biblioteca arquitetural só deve entrar quando existir:

- requisito explícito;
- risco concreto que ela reduz;
- alternativa mais simples considerada;
- custo operacional aceito;
- estratégia de teste e observabilidade.
