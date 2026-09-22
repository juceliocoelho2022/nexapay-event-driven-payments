# NexaPay Production Hardening

This document turns resilience and observability features into an explicit operational contract for the portfolio environment.

> These are **technical portfolio/lab targets**, not a commercial SLA. They are designed to demonstrate how the system would be operated, measured and diagnosed.

## 1. Service Level Indicators and targets

| Signal | SLI | Current technical target | Window |
|---|---|---:|---|
| Availability | Prometheus target availability per service | >= 99% | rolling 5 min / dashboard window |
| HTTP errors | HTTP 5xx / total HTTP requests | < 1% | rolling 5 min |
| HTTP latency | mean HTTP request latency | < 500 ms | rolling 5 min |
| Outbox recovery | pending Outbox batch | returns to 0 | <= 10 min after transient failure |
| DLT | records published to DLT | 0 during normal operation | any occurrence triggers investigation |

### Why availability is a proxy

The current availability SLI is derived from Prometheus `up`. It proves that the metrics endpoint is reachable; it is **not the same as user-perceived availability**.

A production evolution should add synthetic/business-level probes for critical flows such as PIX creation and status retrieval.

### Why mean latency is used today

The current Micrometer setup supports a reliable mean-latency calculation from request sum/count metrics. A stronger production target should use **p95/p99 histograms** once percentile histograms are explicitly enabled and validated.

The project documents this limitation instead of presenting mean latency as a percentile.

## 2. Error budget interpretation

A 99% availability objective corresponds to a 1% unavailability budget.

As a production reference, over 30 days that is approximately **7h12m**. The local portfolio environment is not expected to run continuously for 30 days; the number is included to demonstrate the error-budget concept.

The operational rule is:

- normal changes continue while the system is within target;
- repeated SLO breaches prioritize reliability work over adding architecture;
- a DLT increase is investigated even if the HTTP SLO remains healthy.

## 3. Retry budget

### Ledger and Fraud Kafka consumers

Current configuration:

- fixed backoff: **1000 ms**;
- max retries: **2**;
- behavior: initial delivery + up to 2 retries for retryable failures;
- exhausted retry budget: publish to the corresponding `.DLT`;
- malformed domain payloads are marked non-retryable and go directly to recovery/DLT.

This prevents indefinite retries from blocking a consumer partition.

### Payment Outbox -> Kafka publisher

Current Resilience4j policy:

- max attempts: **3**;
- first wait: **500 ms**;
- exponential multiplier: **2**;
- Kafka publish timeout: **10 s** per attempt;
- circuit breaker sliding window: **10 calls**;
- minimum calls: **5**;
- failure threshold: **50%**;
- open state: **15 s**;
- half-open probes: **3**.

The retry budget is intentionally bounded. When Kafka is unavailable for longer periods, the Outbox retains the event and the publisher must recover later instead of retrying forever inside one call.

## 4. Prometheus recording and alert rules

Versioned rules live in:

`observability/prometheus/slo-rules.yml`

They provide:

- per-service availability ratio;
- per-service HTTP 5xx ratio;
- per-service mean latency;
- aggregate Outbox pending count;
- alerts for availability, 5xx, latency, stuck Outbox and DLT publication.

Prometheus validates the rules through `promtool` in CI.

## 5. Failure drill matrix

| Drill | Failure introduced | Expected behavior | Recovery evidence |
|---|---|---|---|
| Fraud PostgreSQL outage | stop `postgres-fraud` | consumer retries, retry budget exhausts, record reaches DLT | DB returns, controlled replay persists decision |
| Kafka outage | stop `kafka` | Outbox publisher fails/retries; pending Outbox grows; breaker may open | Kafka returns; publisher resumes; backlog returns to zero |
| Fraud service outage | stop `fraud-service` in prod-like compose | Kafka retains events while consumer is unavailable | service returns and consumes backlog without duplicated business state |
| Malformed event | invalid JSON/domain payload | non-retryable failure is isolated quickly | DLT inspected; replay only after payload/cause is corrected |

## 6. Drill A — PostgreSQL failure + DLT + replay

This scenario is already automated by the Sprint 6 resilience suite.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-sprint6-resilience.ps1
```

The suite verifies:

1. malformed Ledger payload -> DLT;
2. Ledger PostgreSQL outage -> retry -> DLT -> DB recovery;
3. malformed Fraud payload -> DLT;
4. Fraud PostgreSQL outage -> retry -> DLT -> DB recovery;
5. controlled Fraud DLT replay -> decision persisted.

The replay is deliberately explicit and defaults to dry-run when called directly:

```powershell
.\scripts\replay-dlt.ps1 -Route fraud-payment -Marker "<unique-marker>"
```

Only after inspection:

```powershell
.\scripts\replay-dlt.ps1 -Route fraud-payment -Marker "<unique-marker>" -Replay
```

The DLT record is not deleted. It remains an immutable quarantine/audit record.

## 7. Drill B — Kafka outage

Precondition: Payment Service is running and there is at least one normal flow available to create an Outbox event.

Baseline:

```powershell
docker compose up -d kafka prometheus grafana
docker compose ps
```

Introduce failure:

```powershell
docker stop nexapay-kafka
```

During the outage, observe:

```promql
sum(nexapay_outbox_batch_pending)
sum(increase(nexapay_outbox_publish_failures_total[10m]))
sum(increase(nexapay_resilience_kafka_publish_attempts_total{result="failure"}[10m]))
```

Expected behavior:

- database transaction that creates the Outbox row remains independent from Kafka availability;
- publisher attempts fail within the bounded retry policy;
- pending Outbox records remain available for a later cycle;
- circuit breaker can open under sustained failures;
- no manual deletion of Outbox records is performed.

Recover:

```powershell
docker start nexapay-kafka
docker exec nexapay-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Success condition: publisher resumes and `nexapay_outbox_batch_pending` returns to zero.

## 8. Drill C — downstream Fraud Service outage

This drill uses the prod-like compose because application services are containerized there.

```powershell
docker compose --env-file .env.production -f docker-compose.prod.yml stop fraud-service
```

Generate normal payment events while Fraud is unavailable.

Expected behavior:

- Kafka retains the records;
- Payment creation is not coupled to Fraud process availability;
- no Fraud decision is produced while the consumer is stopped.

Recover:

```powershell
docker compose --env-file .env.production -f docker-compose.prod.yml start fraud-service
```

Success conditions:

- Fraud resumes consumption;
- accumulated events are processed;
- consumer idempotency prevents duplicate business effects if a record is redelivered.

## 9. Incident runbook

### Payment remains PENDING / downstream processing is missing

1. Check `/actuator/health` for Gateway, Payment and Fraud.
2. Locate the payment by correlation/trace id.
3. Confirm payment persistence.
4. Confirm corresponding Outbox record.
5. Check Outbox pending metric.
6. Check Kafka availability.
7. Check retry and DLT metrics.
8. Search Tempo for producer/consumer spans.
9. Search Loki by correlation id.
10. Fix the cause before replay.

### DLT increases

1. identify source topic and service;
2. inspect exception/logs;
3. classify malformed payload vs transient dependency failure;
4. verify the original dependency has recovered;
5. execute replay script **without** `-Replay`;
6. confirm exactly one unique record is selected;
7. execute controlled replay;
8. verify consumer-side idempotency and final state.

### HTTP 5xx SLO breach

1. identify affected service;
2. compare request rate and 5xx ratio;
3. inspect slow/error traces;
4. correlate with DB/Kafka health and pool saturation;
5. compare deploy/change timing;
6. recover dependency or roll back the responsible change;
7. confirm the SLI returns below 1%.

## 10. Evidence checklist

When running the drills locally, capture:

- Grafana Technical SLOs before/during/after;
- Grafana Resilience dashboard;
- one Tempo trace showing the affected flow;
- Loki logs filtered by correlation id;
- terminal output of the resilience script;
- DLT dry-run and controlled replay output.

Store only sanitized screenshots/log excerpts. Do not commit secrets, JWTs or credentials.

## 11. Definition of done for issue #14

- [x] SLI/SLO targets documented.
- [x] Retry budget documented from current configuration.
- [x] Prometheus recording and alert rules versioned.
- [x] CI validation with promtool.
- [x] PostgreSQL/DLT/replay automated scenario documented.
- [x] Kafka and downstream outage drills documented.
- [x] Incident runbook documented.
- [ ] Runtime evidence from the current local environment captured and reviewed.
- [ ] Confirm SLO dashboard/rules after executing all drills end-to-end.
