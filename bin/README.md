# Idempotent Background Transaction Processing Service

FDE L2 Assessment — Java, Spring Boot 2.7, MySQL 8, JPA

---

## Quick Start

### Prerequisites

- JDK 8+
- Maven 3.6+
- MySQL 8 running on `localhost:3306`

### 1. Configure Database

Edit `src/main/resources/application.yml`:

```yaml
spring.datasource.url=jdbc:mysql://localhost:3306/transaction_processor?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD
```

The database `transaction_processor` will be auto-created. JPA `ddl-auto=update` will create tables.

Alternatively, run the schema manually:

```bash
mysql -u root -p < src/main/resources/schema.sql
```

### 2. Build and Run

```bash
mvn clean package -DskipTests
java -jar target/transaction-processor-1.0.0.jar
```

The application starts on `http://localhost:8080`.

### 3. Run Tests

```bash
mvn clean test
```

Tests use the same MySQL database (`transaction_processor_test`). Tables are auto-created and dropped between test classes.

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/transactions` | Submit a single transaction |
| `POST` | `/api/transactions/batch` | Submit multiple transactions |
| `POST` | `/api/transactions/process` | Trigger synchronous processing |
| `GET` | `/api/transactions/summary` | Get processing summary |
| `GET` | `/api/transactions` | List all transactions |
| `GET` | `/api/transactions/{id}` | Get transaction by ID |

---

## Sample Input & Output

### Submit a transaction

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-2001",
    "requestId": "REQ-1",
    "accountId": "ACC-201",
    "transactionType": "CREDIT",
    "amount": 100.00,
    "currency": "USD",
    "sequenceNumber": 1
  }'
```

**Response:**

```json
{
  "valid": true,
  "transactionId": "TXN-2001",
  "requestId": "REQ-1",
  "status": "RECEIVED"
}
```

### Submit batch (assessment test data)

```bash
curl -X POST http://localhost:8080/api/transactions/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"transactionId":"TXN-2002","requestId":"REQ-2","accountId":"ACC-201","transactionType":"DEBIT","amount":40,"currency":"USD","sequenceNumber":2},
    {"transactionId":"TXN-2001","requestId":"REQ-1","accountId":"ACC-201","transactionType":"CREDIT","amount":100,"currency":"USD","sequenceNumber":1},
    {"transactionId":"TXN-2002","requestId":"REQ-2-DUP","accountId":"ACC-201","transactionType":"DEBIT","amount":40,"currency":"USD","sequenceNumber":2},
    {"transactionId":"TXN-2003","requestId":"REQ-3","accountId":"ACC-201","transactionType":"CREDIT","amount":25,"currency":"USD","sequenceNumber":3},
    {"transactionId":"TXN-2004","requestId":"REQ-4","accountId":"ACC-202","transactionType":"DEBIT","amount":-10,"currency":"USD","sequenceNumber":4},
    {"transactionId":"TXN-2005","requestId":"REQ-5","accountId":"ACC-203","transactionType":"CREDIT","amount":500,"currency":"USD","sequenceNumber":5}
  ]'
```

### Trigger processing

```bash
curl -X POST http://localhost:8080/api/transactions/process
```

### Get summary

```bash
curl http://localhost:8080/api/transactions/summary
```

**Expected output:**

```json
{
  "totalSubmitted": 5,
  "statusCounts": {
    "PROCESSED": 3,
    "DUPLICATE": 1,
    "FAILED": 1
  },
  "processedAt": 1708617600000
}
```

### Expected outcomes per test data row

| Input | transactionId | requestId | Expected Outcome |
|---|---|---|---|
| 1 | TXN-2002 | REQ-2 | PROCESSED (after TXN-2001 resolves — sequence ordering) |
| 2 | TXN-2001 | REQ-1 | PROCESSED (seq=1, processed first) |
| 3 | TXN-2002 | REQ-2-DUP | DUPLICATE (transactionId TXN-2002 already exists) |
| 4 | TXN-2003 | REQ-3 | PROCESSED (seq=3, after seq 1,2 resolve) |
| 5 | TXN-2004 | REQ-4 | FAILED (negative amount — validation error) |
| 6 | TXN-2005 | REQ-5 | PROCESSED (retry simulation: fails once, succeeds on retry) |

---

## Transaction Status Lifecycle

```
RECEIVED → PROCESSING → PROCESSED    (success path)
           → PROCESSING → RETRY_PENDING → PROCESSING → PROCESSED  (retry path)
           → PROCESSING → FAILED              (retry exhausted)

Duplicate at submission → rejected with 409 CONFLICT
Invalid input → rejected with 400 BAD REQUEST
```

---

## Project Structure

```
src/main/java/com/fde/transaction/
├── TransactionApplication.java          # Spring Boot entry point
├── config/SchedulingConfig.java         # @EnableScheduling
├── dto/
│   ├── TransactionRequestDTO.java       # Input DTO with validation
│   └── ProcessingSummaryDTO.java        # Output summary
├── enums/
│   ├── TransactionStatus.java           # Status enum
│   └── TransactionType.java             # CREDIT, DEBIT, REVERSAL
├── exception/
│   ├── DuplicateTransactionException.java
│   └── TransactionValidationException.java
├── model/
│   ├── TransactionRequest.java          # JPA entity
│   ├── AccountBalance.java              # JPA entity with @Version
│   └── ProcessingLog.java               # Audit log entity
├── repository/
│   ├── TransactionRequestRepository.java
│   ├── AccountBalanceRepository.java
│   └── ProcessingLogRepository.java
├── rest/
│   ├── TransactionController.java       # REST endpoints
│   └── GlobalExceptionHandler.java      # Error → HTTP mapping
└── service/
    ├── TransactionProcessorService.java  # Core idempotent processor
    ├── TransactionValidationService.java # Input validation
    ├── BackgroundJobService.java         # Scheduled batch runner
    └── TransientFailureSimulator.java    # Failure injection for testing

src/test/java/com/fde/transaction/
├── TransactionApplicationTests.java      # Context loads
├── rest/TransactionControllerIntegrationTest.java
└── service/
    ├── IdempotencyTest.java
    ├── OutOfOrderTest.java
    ├── RetryTest.java
    └── ValidationTest.java
```

---

## Running in Eclipse

1. File → Import → Existing Maven Projects
2. Select the `SanTest` directory
3. Right-click project → Run As → Maven Build → `clean test`
4. To run the app: Right-click `TransactionApplication.java` → Run As → Java Application

---

## Design Document

See [DESIGN.md](DESIGN.md) for full architecture, trade-offs, and production hardening recommendations.
