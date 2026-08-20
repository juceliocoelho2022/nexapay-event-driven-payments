# Sprint 11 — Distributed Observability & Correlation

A Sprint 11 evolui a observabilidade do NexaPay com rastreabilidade distribuída entre o fluxo HTTP, Transactional Outbox, Kafka e consumidores assíncronos.

## Status da validação

- [x] `correlationId` recebido via `X-Correlation-Id`
- [x] propagação do `correlationId` no Payment Service
- [x] persistência e recuperação do contexto no Transactional Outbox
- [x] propagação do `correlationId` pelo evento Kafka
- [x] recuperação do contexto no Fraud Service
- [x] MDC nos logs do produtor e consumidor
- [x] coleta de logs com Grafana Alloy
- [x] centralização no Loki
- [x] dashboard `NexaPay Distributed Logs`
- [x] filtro por serviço e `correlationId`
- [x] validação E2E Payment → Outbox → Kafka → Fraud
- [ ] logs estruturados em JSON
- [ ] consolidação dos SLOs técnicos finais

## Fluxo validado

```text
HTTP POST /api/v1/payments/pix
        │
        │ X-Correlation-Id
        ▼
Payment Service
        │
        │ Transactional Outbox
        ▼
Outbox Publisher
        │
        │ Kafka
        ▼
Fraud Service
        │
        ▼
Fraud Analysis
```

O mesmo `correlationId` é preservado ao longo do processamento assíncrono, permitindo localizar no Grafana os registros relacionados a uma única operação distribuída.

## Evidência E2E

Correlation ID validado:

```text
nexapay-grafana-001
```

Payment Service:

```text
[correlationId=nexapay-grafana-001] Outbox publicado.
```

Fraud Service — recebimento:

```text
[correlationId=nexapay-grafana-001] Received payment event for fraud analysis.
```

Fraud Service — decisão:

```text
[correlationId=nexapay-grafana-001] Fraud analysis completed. decision=APPROVED, riskScore=20
```

Essa validação demonstra que uma operação pode ser rastreada entre serviços mesmo atravessando uma fronteira assíncrona baseada em Kafka.

## Pipeline de logs

```text
Microsserviços
      │
      │ arquivos de log
      ▼
Grafana Alloy
      │
      ▼
Loki
      │
      ▼
Grafana
```

Os diretórios de log dos serviços são montados no container do Alloy. O Alloy adiciona o label `service_name` e envia os registros ao Loki.

## Dashboard

Dashboard provisionado:

```text
NexaPay Distributed Logs
```

Consulta base em LogQL:

```logql
{service_name=~"$service",service_name!=""} |= "$correlationId"
```

O dashboard permite selecionar todos os serviços e buscar uma operação pelo mesmo `correlationId`, exibindo os registros em ordem temporal.

## Resultado

A rastreabilidade distribuída foi validada no fluxo:

```text
Payment Service
  → Transactional Outbox
  → Kafka
  → Fraud Service
  → Fraud Decision
```

com preservação do mesmo identificador de correlação entre produtor e consumidor.

## Próximas evoluções

1. estruturar logs em JSON;
2. extrair `correlationId`, `eventId` e outros campos relevantes como metadados pesquisáveis quando apropriado;
3. consolidar dashboards operacionais;
4. definir e validar SLOs técnicos;
5. adicionar ao portfólio uma captura do dashboard validado em execução.
