# NexaPay — Business-Driven Portfolio Repositioning Design

Date: 2026-10-02  
Status: approved design awaiting implementation plan

## 1. Intent

Reposition the NexaPay repository so a recruiter, Tech Lead or backend interviewer understands the financial problem, protected invariants, architecture decisions, trade-offs and implementation evidence before encountering a technology inventory.

This is a documentation and portfolio-architecture change. It does not change Java, database schemas, Kafka contracts, Docker, dependencies or runtime behavior in this slice.

## 2. Primary audience

- Java Backend recruiters and hiring managers;
- Backend/Software Engineers and Tech Leads;
- interviewers evaluating distributed systems, financial consistency and resiliency;
- engineers reviewing event-driven architecture, observability and concurrency decisions.

## 3. Current repository baseline

The repository already contains strong engineering evidence that must be preserved and surfaced rather than duplicated:

- `README.md` with service architecture, sprints, endpoints and runtime evidence;
- `docs/business-case.md` in PR #29 with BR-001..BR-010, FR/NFR and acceptance criteria;
- `docs/engineering-decisions.md` with distributed-systems trade-offs;
- `docs/adr/` with decisions for SDD/AI, scheduled PIX, recurring PIX, cancellation, fraud state machine, manual review, review queue and SLA;
- `docs/specs/` with feature specifications and acceptance criteria;
- `AGENTS.md` with engineering invariants and Definition of Done;
- automated tests including JUnit, MockMvc and Testcontainers;
- observability artifacts using Prometheus, Grafana, Loki, Tempo and OpenTelemetry.

The design must treat these files as canonical evidence and avoid creating redundant ADRs or parallel specifications for the same behavior.

## 4. Core business problem

A distributed payment platform must preserve financial invariants even when clients retry requests, messages are redelivered, dependencies fail temporarily, multiple workers compete for the same state and asynchronous services observe the same operation at different times.

The central portfolio narrative is:

> The NexaPay preserves financial invariants under retries, redelivery, concurrency and partial failures by combining idempotent boundaries, transactional persistence, reliable event publication, atomic state transitions and end-to-end observability.

The project is a portfolio simulation. It must not claim production transaction volume, real-world availability, regulatory certification or unmeasured performance characteristics.

## 5. Canonical business rules

`docs/business-case.md` remains the canonical business-rules document. The current BR-001..BR-010 model is preserved:

- **BR-001 — Payment idempotency:** one `Idempotency-Key` represents one logical payment intention.
- **BR-002 — Monetary precision:** monetary values use decimal representation appropriate to the financial domain.
- **BR-003 — Concurrent debit safety:** concurrent balance changes must preserve the account invariant.
- **BR-004 — Reliable publication:** domain state and outbound-event intent are persisted atomically through Transactional Outbox.
- **BR-005 — At-least-once delivery:** repeated Kafka delivery must not produce duplicate business effects.
- **BR-006 — Fraud decision:** risk analysis must produce an explicit, traceable decision state.
- **BR-007 — Scheduled PIX:** execution cannot occur before eligibility, and concurrent schedulers must compete through an atomic claim.
- **BR-008 — Recurring PIX:** recurrence defines occurrence materialization without duplicating the payment execution pipeline.
- **BR-009 — Concurrent cancellation:** execution and cancellation races must resolve through an atomic valid state transition.
- **BR-010 — Auditability:** sensitive administrative actions preserve actor, timestamp, reason and relevant context.

No new rule identifier is introduced unless the current repository evidence requires one.

## 6. Traceability model

The main improvement to `docs/business-case.md` will be an explicit traceability matrix:

```text
Business Rule
    ↓
Functional / Non-Functional Requirement
    ↓
Service / Component / Pattern
    ↓
SPEC / ADR / Test / Operational Evidence
```

Representative mappings:

| Rule | Requirement | Implementation | Evidence |
|---|---|---|---|
| BR-001 | FR-001, NFR-005 | Payment Service + `Idempotency-Key` | PIX SPEC + automated idempotency tests |
| BR-003 | FR-003, NFR-005 | Account Service + PostgreSQL locking | account/concurrency tests |
| BR-004 | FR-004, NFR-001 | PostgreSQL + Transactional Outbox | service implementation + ADR/engineering decisions |
| BR-005 | FR-005/FR-006, NFR-002 | Kafka consumers + retry/DLT + deduplication | consumer tests and replay behavior |
| BR-007 | FR-008 | scheduled PIX + atomic claim | Scheduled PIX SPEC + ADR-006 + Testcontainers |
| BR-009 | FR-010 | conditional/atomic state transition | Cancellation SPEC + ADR-008 + concurrency tests |
| BR-010 | FR-010/FR-011 | audit records + JWT actor | cancellation/manual-review specs and tests |

The implementation plan must verify exact file/test names before finalizing each evidence reference.

## 7. README redesign

The first substantive sections of `README.md` will be reorganized into this order:

1. one-sentence product/case description;
2. `Business Problem`;
3. `Financial Failure Modes`;
4. `Core Business Rules`;
5. `Critical PIX Flow`;
6. `Engineering Decisions & Trade-offs`;
7. `Evidence`;
8. `Current Capabilities / Sprint History`;
9. stack, execution instructions and detailed references.

The README must answer these questions before listing the stack:

- What financial problem is being solved?
- What must never happen?
- How are retries and duplicate delivery handled?
- How does the system avoid a naive PostgreSQL + Kafka dual write?
- How are concurrency races resolved?
- How can an engineer diagnose a pending or failed payment?

## 8. Critical payment flow

The recruiter-facing architecture should center on the payment lifecycle rather than the service list alone:

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
  ↓
Outbox Publisher
  ↓
Kafka
  ↓
Fraud Service
  ├── APPROVED
  ├── REVIEW
  └── BLOCKED
  ↓
FraudDecisionMade event
  ↓
Payment state transition
  ├── COMPLETED
  ├── REVIEW
  └── REJECTED
```

Scheduled, recurring, cancellation and manual-review flows remain linked as extensions of this core lifecycle.

## 9. Engineering decisions to surface

### 9.1 PostgreSQL as transactional source of truth

**Problem:** financial state requires atomic transitions, concurrency control and durable history.  
**Decision:** PostgreSQL holds transactional state and participates in locking/conditional-update strategies.  
**Trade-off:** correctness-oriented coordination can reduce concurrency and requires careful transaction design.

### 9.2 Transactional Outbox

**Problem:** writing to PostgreSQL and publishing to Kafka independently creates a dual-write failure window.  
**Decision:** persist business change and outbound-event intent in the same local database transaction, then publish asynchronously.  
**Trade-off:** adds relay/publisher state, retry, backlog monitoring and operational complexity.

### 9.3 Kafka + at-least-once semantics

**Problem:** downstream processing should be decoupled and replayable.  
**Decision:** Kafka is used for asynchronous domain events. Consumers tolerate redelivery and remain idempotent.  
**Trade-off:** the system must explicitly manage duplicates, partitions, ordering boundaries, retry and DLT. The repository must not claim exactly-once global processing.

### 9.4 Atomic state transitions

**Problem:** schedulers, cancellations and reviewers can race over the same logical state.  
**Decision:** use database-level claims/conditional updates where the existing feature requires one winner.  
**Trade-off:** lifecycle rules become explicit in persistence logic and require integration/concurrency tests.

### 9.5 Observability by design

**Problem:** distributed failures cannot be diagnosed from HTTP status alone.  
**Decision:** preserve correlation/trace context and expose metrics, logs and traces across HTTP and Kafka boundaries.  
**Trade-off:** instrumentation and telemetry infrastructure add operational cost and must remain useful rather than decorative.

## 10. `docs/engineering-decisions.md` evolution

The existing document will not be replaced. It will be strengthened with explicit links between each decision and:

- the business rule/risk it protects;
- the service/component responsible;
- the relevant SPEC/ADR where one exists;
- the trade-off paid for the decision;
- the observable evidence used to validate it.

A compact decision matrix may be added near the top so the document becomes an index of architectural reasoning.

## 11. Portfolio interview case study

Create `docs/PORTFOLIO_CASE_STUDY.md` containing:

### 60-second explanation

Sequence:

```text
financial problem
→ protected invariant
→ transaction + Outbox
→ Kafka + idempotent consumers
→ fraud/state transition
→ evidence
```

### 3–5 minute technical explanation

Cover:

- idempotent PIX creation;
- monetary precision and account concurrency;
- Transactional Outbox;
- Kafka at-least-once semantics;
- fraud decision lifecycle;
- scheduled/recurring execution;
- cancellation races;
- manual fraud review;
- observability and diagnostic workflow.

### Interview Q&A

At minimum:

- Why Kafka?
- Why Transactional Outbox?
- Why not exactly-once?
- How do you prevent duplicate payments?
- How do you protect account balance under concurrency?
- How do scheduled payment and cancellation races resolve?
- Why Testcontainers?
- How would you investigate a payment stuck in `PENDING`?
- What would change for real production scale?

Answers must distinguish implemented evidence from future improvements.

## 12. Evidence strategy

Claims should link to existing repository evidence where possible:

- PIX idempotency tests;
- Account locking/concurrency tests;
- Outbox implementation and tests;
- Kafka retry/DLT/replay protection;
- Fraud state-machine SPEC/ADR/tests;
- Scheduled PIX atomic-claim SPEC/ADR/Testcontainers;
- Recurring PIX occurrence materialization;
- cancellation concurrency tests;
- manual fraud review and queue ownership tests;
- CI workflows;
- Grafana/Prometheus/Loki/Tempo configuration and documented trace evidence;
- production-hardening artifacts where the runtime evidence is actually available.

No throughput, latency, availability or business-success number should be introduced unless it already has explicit repository evidence and is labeled with its test/runtime context.

## 13. Current vs. roadmap discipline

The repository currently mixes completed sprints and in-progress hardening work. The repositioning must keep these distinctions explicit.

Examples:

- completed payment/fraud/scheduling/cancellation capabilities may be described as implemented;
- Sprint 12 runtime-evidence completion remains incomplete where the README says so;
- Sprint 19 remains in evolution where documented;
- cloud/production scale, regulatory compliance, formal real-world SLO achievement and business volume must not be implied from local/demo artifacts.

## 14. Out of scope

This slice will not:

- alter payment behavior;
- add or remove microservices;
- change Kafka topics or payload contracts;
- modify database schemas or Flyway migrations;
- change security authorities;
- refactor Java production code;
- change Docker Compose or cloud deployment;
- create duplicate ADRs for already documented decisions;
- claim PCI, BACEN, security or regulatory certification;
- invent production metrics.

Any behavioral change discovered as necessary during implementation must stop this documentation slice and receive its own design/spec.

## 15. Success criteria

After reading the first portion of the README, a reviewer should be able to explain:

1. why duplicate payment execution is a business risk;
2. how `Idempotency-Key`, PostgreSQL and Outbox protect the write path;
3. why Kafka is used and why consumers must be idempotent;
4. how concurrency-sensitive flows choose one valid state transition;
5. how fraud decisions affect payment lifecycle;
6. how the system is diagnosed when asynchronous processing fails;
7. which claims are implementation evidence and which items remain roadmap.

The final repository should read as an engineering case about preserving financial invariants in a distributed system, not as a catalog of frameworks.