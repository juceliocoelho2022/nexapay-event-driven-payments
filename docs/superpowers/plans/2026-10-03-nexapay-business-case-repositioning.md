# NexaPay Business-Case Repositioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the NexaPay portfolio documentation so reviewers understand the payment-domain problem, financial invariants, failure modes, architecture decisions and evidence before the technology stack.

**Architecture:** This is a documentation-only repositioning on the existing `docs/business-driven-engineering` branch and PR #29. Existing SPECs, ADRs, implementation claims and operational evidence are preserved; the change adds traceability and restructures the README and supporting documents around business risk -> invariant -> technical decision -> evidence.

**Tech Stack:** Markdown and existing GitHub documentation; implementation evidence already present in Java 21, Spring Boot, PostgreSQL, Kafka, Transactional Outbox, JWT/Spring Security, Testcontainers, Prometheus/Grafana, Loki/Alloy and OpenTelemetry/Tempo.

**Spec:** `docs/superpowers/specs/2026-10-02-nexapay-business-case-repositioning-design.md`

## Global Constraints

- Do not alter Java code, database migrations, Kafka topics, HTTP/event contracts, Docker Compose or dependencies.
- Keep `docs/business-case.md` as the canonical business/domain document; do not split the same content into redundant files.
- Preserve BR-001 through BR-010 and the existing FR/NFR identifiers.
- Do not create new ADRs when an existing SPEC/ADR already documents the decision.
- Do not claim global exactly-once semantics; the project uses at-least-once delivery plus idempotent boundaries.
- Do not present unfinished Sprint 12 runtime evidence or Sprint 19 as fully complete.
- Do not add unsupported throughput, latency, availability or production-scale claims.
- Keep current SPECs/ADRs as the source of truth for feature-specific behavior.

## Review Focus

1. **Duplicate payment intent:** README/business-case wording must distinguish HTTP idempotency from Kafka consumer idempotency and must not imply global exactly-once.
2. **Concurrency:** balance debit, scheduled execution and cancellation claims must be described as database/atomic-transition concerns, not as guarantees from Kafka.
3. **Fraud lifecycle:** `APPROVED`, `REVIEW`, `BLOCKED` and the resulting payment transitions must match the implemented state machine and existing SPEC/ADR wording.
4. **Evidence status:** Sprint 12 runtime evidence and Sprint 19 must remain explicitly incomplete/evolving where the repository already says so.
5. **Traceability:** every headline business rule mentioned in the README must point to an existing requirement, SPEC/ADR or concrete test/evidence source.

---

### Task 1: Strengthen the canonical business case with traceability

**Files:**
- Modify: `docs/business-case.md`

**Interfaces:**
- Consumes: BR-001..BR-010, FR-001..FR-011, NFR-001..NFR-006 already defined in the file; existing SPECs and ADRs under `docs/specs/` and `docs/adr/`.
- Produces: a canonical traceability matrix later referenced by the README and portfolio case study.

- [ ] **Step 1: Add a `Traceability Matrix` section after the NFRs**

Add columns:

```text
Business Rule | Requirements | Service / Component | SPEC / ADR | Evidence / Test
```

Populate only relationships supported by existing repository artifacts. At minimum include BR-001, BR-003, BR-004, BR-005, BR-007, BR-009 and BR-010.

- [ ] **Step 2: Verify BR-001 duplicate-intent semantics**

Expected wording: repeated `Idempotency-Key` reuses the original logical operation; it does not claim that every downstream side effect is globally exactly-once.

- [ ] **Step 3: Verify concurrency wording**

Check BR-003, BR-007 and BR-009 against the README/SPEC/ADR descriptions of `PESSIMISTIC_WRITE`, atomic claims and conditional state transitions.

Expected: no statement attributes those guarantees to Kafka.

- [ ] **Step 4: Verify evidence-status wording**

Expected: runtime-evidence gaps and Sprint 19 evolution are not presented as complete.

- [ ] **Step 5: Commit**

```bash
git add docs/business-case.md
git commit -m "docs: add NexaPay rule-to-evidence traceability"
```

---

### Task 2: Reframe engineering decisions around business risk and trade-offs

**Files:**
- Modify: `docs/engineering-decisions.md`

**Interfaces:**
- Consumes: canonical rules from `docs/business-case.md` and feature ADRs already present in `docs/adr/`.
- Produces: concise cross-cutting rationale linked from the README.

- [ ] **Step 1: Add rule references to each major decision**

Map at least:

- Microservices/Event-Driven -> BR-004, BR-005, BR-006
- Transactional Outbox -> BR-004, BR-005
- PostgreSQL locking / atomic transitions -> BR-003, BR-007, BR-009
- Kafka at-least-once -> BR-005
- Security / JWT / authorities -> BR-010 and protected fraud/cancellation operations
- Observability -> operational diagnosis across Payment/Fraud/Kafka flows

- [ ] **Step 2: Add a dedicated section for PostgreSQL locking / atomic transitions**

Explain the problem (competing debits, scheduler claims, cancellation/review races), the decision (database locking/conditional transitions), and the trade-off (contention + need for real integration tests).

- [ ] **Step 3: Add a dedicated security decision section**

Explain why JWT/authorities exist for sensitive operations; keep claims aligned with current roles/permissions documented in the README.

- [ ] **Step 4: Preserve the existing monolith-vs-microservices trade-off**

Expected: documentation still states that a smaller real product could reasonably start as a modular monolith.

- [ ] **Step 5: Commit**

```bash
git add docs/engineering-decisions.md
git commit -m "docs: tie NexaPay engineering decisions to domain risks"
```

---

### Task 3: Create the interview-ready portfolio case study

**Files:**
- Create: `docs/PORTFOLIO_CASE_STUDY.md`

**Interfaces:**
- Consumes: `docs/business-case.md`, `docs/engineering-decisions.md`, existing SPECs/ADRs, README evidence.
- Produces: recruiter/interviewer-facing narrative linked from the README.

- [ ] **Step 1: Write the 60-second explanation**

Use this exact narrative order:

```text
payment-domain problem
-> primary invariants
-> idempotency + PostgreSQL + Outbox
-> Kafka at-least-once + idempotent consumers
-> fraud/state transitions
-> evidence/tests/observability
```

- [ ] **Step 2: Write the 3-5 minute technical explanation**

Cover:

- immediate PIX creation;
- idempotency;
- Outbox dual-write protection;
- Payment -> Kafka -> Fraud -> FraudDecisionMade -> Payment state transition;
- Account balance concurrency;
- Scheduled PIX atomic claim;
- cancellation vs execution race;
- retry/DLT/replay;
- metrics/logs/traces.

- [ ] **Step 3: Add interview Q&A**

Include answers for all questions listed in the approved spec, especially `Why Kafka?`, `Why Outbox?`, `Why not exactly-once?`, duplicate payments, concurrent debit, scheduled PIX, cancellation race, fraud lifecycle, PENDING diagnosis and production-scale evolution.

- [ ] **Step 4: Add an `Evidence Map` section**

Link each major claim to an existing SPEC, ADR, test category or operational artifact. Do not invent test names if the exact test name has not been verified.

- [ ] **Step 5: Mark future/unfinished work explicitly**

Expected: production hardening final runtime evidence and Sprint 19 are called out as evolving/incomplete.

- [ ] **Step 6: Commit**

```bash
git add docs/PORTFOLIO_CASE_STUDY.md
git commit -m "docs: add NexaPay portfolio case study"
```

---

### Task 4: Rebuild the README opening around the payment business case

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: the primary recruiter-facing entry point while preserving current detailed sprint/technical documentation.

- [ ] **Step 1: Preserve existing content before restructuring**

Retain logo/images, current sprint statuses, service endpoints, architecture, observability evidence, execution instructions and links to SPECs/ADRs.

- [ ] **Step 2: Replace the stack-first opening**

The first substantive sections must be:

1. `Business Problem`
2. `Financial Invariants / Failure Modes`
3. `Critical Payment Flow`
4. `Engineering Decisions & Trade-offs`
5. `Evidence / Tests / Observability`
6. `Architecture`
7. `Technical Snapshot / Stack`
8. existing sprint/detail sections

- [ ] **Step 3: Add the business-first one-sentence positioning**

Use the approved meaning:

> NexaPay modela um sistema de pagamentos distribuído que precisa preservar invariantes financeiras diante de retries, redelivery, concorrência e falhas parciais.

- [ ] **Step 4: Add a compact failure-mode table**

Include at least:

```text
repeated client request -> duplicate payment -> Idempotency-Key
DB commit + broker failure -> missing event -> Transactional Outbox
Kafka redelivery -> duplicated effect -> idempotent consumer
concurrent debit -> inconsistent balance -> PostgreSQL locking/transaction
scheduler race -> duplicate scheduled execution -> atomic claim
cancel vs execute -> invalid double transition -> conditional state transition
operational failure -> poor diagnosis -> metrics + logs + traces
```

- [ ] **Step 5: Add the critical PIX flow**

Show:

```text
Client -> Gateway -> Payment -> PostgreSQL + Outbox -> Kafka -> Fraud
-> FraudDecisionMade -> Kafka -> Payment state transition
```

Explicitly state at-least-once + idempotency and no global exactly-once claim.

- [ ] **Step 6: Reframe major technologies as decisions**

For PostgreSQL, Kafka, Outbox, JWT/Security and observability, state the problem solved plus one trade-off/cost.

- [ ] **Step 7: Add documentation links**

Link prominently to:

- `docs/business-case.md`
- `docs/engineering-decisions.md`
- `docs/PORTFOLIO_CASE_STUDY.md`
- existing key SPECs/ADRs
- `docs/production-hardening.md`

- [ ] **Step 8: Verify current vs future claims**

Search wording around Sprint 12, Sprint 19, SLA, production, throughput, latency and scale.

Expected: incomplete/evolving work stays labeled that way and no unsupported quantitative production claim is added.

- [ ] **Step 9: Commit**

```bash
git add README.md
git commit -m "docs: reposition NexaPay around payment invariants"
```

---

### Task 5: Final documentation integrity and PR review

**Files:**
- Verify: `README.md`
- Verify: `docs/business-case.md`
- Verify: `docs/engineering-decisions.md`
- Verify: `docs/PORTFOLIO_CASE_STUDY.md`
- Verify: `docs/specs/**`
- Verify: `docs/adr/**`
- Verify: `docs/production-hardening.md`

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: a review-ready PR #29 with documentation-only changes and consistent claims.

- [ ] **Step 1: Verify traceability**

Expected: every business rule highlighted in the README can be followed to `docs/business-case.md` and then to at least one existing requirement/component/SPEC/ADR/evidence reference.

- [ ] **Step 2: Verify internal links**

Open all newly added relative Markdown links from README, business-case and portfolio case study.

Expected: every link resolves on `docs/business-driven-engineering`.

- [ ] **Step 3: Verify semantic boundaries**

Search for wording that could imply:

- global exactly-once;
- Kafka provides balance consistency;
- unfinished hardening evidence is complete;
- Sprint 19 is complete;
- unmeasured production scale/SLO attainment.

Expected: none of those claims remain.

- [ ] **Step 4: Compare the branch against `main`**

Run:

```bash
git diff --stat main...HEAD
git diff main...HEAD -- README.md docs/
```

Expected: documentation-only changes; no Java, migration, Docker, workflow, dependency or runtime configuration changes.

- [ ] **Step 5: Review PR #29 summary**

Update the PR body if needed so it accurately lists the final README/business-case/engineering-decisions/portfolio-case changes and explicitly states the non-goal of changing production behavior.

- [ ] **Step 6: Final commit only if review corrections are required**

```bash
git add README.md docs/
git commit -m "docs: finalize NexaPay business case repositioning"
```
