# Design Document — Idempotent Background Transaction Processing Service

## FDE L2 Assessment

---

## 1. Architecture Overview

```
┌──────────────┐     POST       ┌──────────────────┐
│  Client      │ ─────────────► │  REST Controller  │
│  (API/File/  │                │  /api/transactions │
│   Queue)     │                └────────┬─────────┘
└──────────────┘                         │ validates
                                         │ + deduplicates
                                         ▼
                                  ┌──────────────────┐
                                  │  JPA Repository   │
                                  │  (MySQL)          │
                                  └────────┬─────────┘
                                           │
                            ┌──────────────┼──────────────┐
                            ▼              ▼              ▼
                     ┌────────────┐ ┌──────────┐ ┌──────────┐
                     │  Requests  │ │ Account  │ │  Audit   │
                     │  table     │ │ Balance  │ │  Log     │
                     │  (UK on    │ │ table    │ │  table   │
                     │   txnId,   │ │ (@Version│ │          │
                     │   reqId))  │ │  lock)   │ │          │
                     └────────────┘ └──────────┘ └──────────┘
                            ▲
                            │ processes
                     ┌──────┴──────────┐
                     │ BackgroundJob   │
                     │ Service         │
                     │ (@Scheduled)    │
                     └─────────────────┘
```

### Layer Breakdown

| Layer | Class | Responsibility |
|---|---|---|
| **API** | `TransactionController` | Accept requests, return status |
| **Validation** | `TransactionValidationService` | Field-level validation, violation reporting |
| **Processing** | `TransactionProcessorService` | Idempotent execution, balance updates, retry logic |
| **Scheduling** | `BackgroundJobService` | Periodic batch runner, synchronous `runOnce()` |
| **Failure sim** | `TransientFailureSimulator` | Deterministic failure injection for retry testing |
| **Persistence** | JPA Repositories | MySQL-backed CRUD with unique constraints |
| **Exception handling** | `GlobalExceptionHandler` | Domain → HTTP error mapping |

---

## 2. Idempotency Design

### Dual-layer protection:

| Layer | Mechanism | Guarantee |
|---|---|---|
| **Application** | Check `requestId` and `transactionId` in repo before insert | Fast rejection, clear error message |
| **Database** | `UNIQUE` constraint on `request_id` and `transaction_id` columns | Absolute guarantee — even under concurrent requests |

### Flow:

1. Client submits request with `requestId` and `transactionId`
2. Controller queries: does `requestId` already exist? → reject as DUPLICATE
3. Controller queries: does `transactionId` already exist? → reject as DUPLICATE
4. If both checks pass, insert with `status = RECEIVED`
5. Background processor picks up `RECEIVED` records
6. Once processed, status moves to `PROCESSED` — future attempts to re-process are skipped (terminal state check)

---

## 3. Duplicate Detection Logic

```
requestId match → DUPLICATE (same request retried)
transactionId match → DUPLICATE (same business txn via different request)
both unique → NEW (persist as RECEIVED)
```

A duplicate is only rejected if the existing record is in a non-FAILED state. This allows re-submission after a permanent failure if the upstream system chooses to retry.

---

## 4. Out-of-Order Handling Strategy

**Chosen approach: Sequence-Based Processing (Option A from the assessment)**

### How it works:

1. Each transaction carries a `sequenceNumber` per account
2. When processing `seq=N`, the processor checks: are all `seq < N` for this account in a terminal state?
3. If yes → proceed with processing
4. If no → mark as `PENDING`, leave for next scheduler cycle

### Trade-off rationale:

| | Option A (Sequence) | Option B (State-machine) | Option C (Latest-state) |
|---|---|---|---|
| **Complexity** | Medium — predecessor check query | High — needs state transition graph | Low — just compare timestamps |
| **Correctness** | Strong — enforces order | Strong — enforces valid transitions | Weakest — drops intermediate states |
| **Fit for domain** | ✅ Credit → Debit ordering matters | ⚠️ Overhead for simple credit/debit | ❌ Financial domains need ordering |

### Query used:

```sql
SELECT * FROM transaction_requests
WHERE account_id = ?
  AND sequence_number < ?
  AND status NOT IN ('PROCESSED', 'DUPLICATE', 'FAILED')
```

If any rows exist, the current request is blocked.

---

## 5. Retry and Failure Handling

### Retry model:

| Parameter | Value |
|---|---|
| Max retries | 3 (configurable per transaction) |
| Backoff | Scheduler interval (2s fixed delay) |
| Idempotency on retry | Guaranteed — `@Transactional` + terminal-state skip + DB unique constraints |

### Flow:

```
PROCESSING → [failure] → RETRY_PENDING (retry_count++)
RETRY_PENDING → PROCESSING → [success] → PROCESSED
RETRY_PENDING → PROCESSING → [failure, count=max] → FAILED
```

### Transient failure simulation:

`TransientFailureSimulator` tracks attempt counts per `transactionId` using `AtomicInteger` (thread-safe, no locks). On first call to `shouldFail(txnId)`, it returns `true` and throws. On subsequent calls, it returns `false`.

This is **deterministic** — tests know exactly when a failure will occur.

---

## 6. Observability Approach

### Logging:

| Level | Event |
|---|---|
| `INFO` | Transaction received, processed, balance updated |
| `WARN` | Duplicate detected, retry scheduled, validation failed |
| `ERROR` | Permanent failure, retry exhausted |
| `DEBUG` | Skipped terminal state, PROCESSING guard |

### Audit log:

Every status transition is written to the `processing_log` table with `transactionId`, `requestId`, `action`, `result`, and `timestamp`.

### Processing summary:

`GET /api/transactions/summary` returns counts grouped by status — enables quick operational visibility.

---

## 7. Data Consistency

- **`@Transactional`** on all processing methods ensures atomic updates: transaction status + balance change either both commit or both rollback
- **Optimistic locking** (`@Version` on `AccountBalance`) prevents concurrent balance corruption — if two threads try to update the same account, one fails with `OptimisticLockException` and is retried
- **DB unique constraints** are the final safety net — even if all application-level guards fail, the DB will reject duplicates

---

## 8. Security and Data Handling

- No sensitive data logged (account numbers are opaque IDs)
- Input validation rejects malformed requests before they reach the processing layer
- No hardcoded secrets; database credentials via Spring Boot externalized config
- Error messages to clients do not expose internal stack traces

---

## 9. AI-Assisted Development

AI tools were used for:
- Rapid scaffolding of JPA entities, repositories, and REST controllers
- Generating boilerplate getters/setters and test structure
- Drafting SQL schema with appropriate constraints and indexes
- Cross-referencing Spring Boot best practices

**Validation applied:**
- All generated code was reviewed for correctness before inclusion
- Test cases were designed to cover the specific assessment requirements, not generic patterns
- Design trade-offs (e.g., sequence-based ordering vs. latest-state) were evaluated independently
- Exception handling and transaction boundaries were manually verified against Spring Boot semantics

---

## 10. Production Hardening Recommendations

| Area | Recommendation | Priority |
|---|---|---|
| **Persistence** | Migrate to PostgreSQL with connection pool monitoring | High |
| **Queue** | Replace scheduled poll with Kafka/SQS consumer | High |
| **Concurrency** | Use `@Async` thread pool for parallel transaction processing | High |
| **Observability** | Add Prometheus metrics (processing rate, latency, error rate) | Medium |
| **Alerting** | Alert on RETRY_PENDING backlog > threshold | Medium |
| **Security** | Add API key / OAuth2 authentication to REST endpoints | Medium |
| **Scalability** | Partition `transaction_requests` table by account_id hash for large volumes | Low |
| **Recovery** | Implement dead-letter queue for permanently failed transactions | Low |
| **Audit** | Make `processing_log` append-only with immutability constraints | Low |

---

## 11. Assumptions

1. Transactions within the same account are ordered by `sequenceNumber`
2. Transactions across different accounts are independent (no cross-account ordering)
3. Currency mismatches between transaction and account balance are not validated (simplified for assessment)
4. The `sequenceNumber` is provided by the upstream system
5. Maximum batch size is not bounded (simplified for assessment)
