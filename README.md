# Distributed Order Processing System

A production-grade, distributed order processing resume project designed for technical interviews. Demonstrates **concurrency control via Redis distributed locking**, **idempotency with SHA-256 payload hashing**, **asynchronous event-driven orchestration with Apache Kafka**, **compensation sagas for payment failures**, and an **interactive React (Vite) demo frontend**.

---

## Architecture Overview

```
                            [ React Frontend (Vite) ]
                           (http://localhost:3000)
                                      │
                         REST API (HTTP / JSON)
                                      │
                                      ▼
                        [ Module 1: order-service ]
                           (http://localhost:8080)
                         ┌────────────┴────────────┐
                         │                         │
                         ▼                         ▼
         [ Redis: lock:inventory:{productId} ]   [ MongoDB: orders & products ]
         (Redisson distributed lock: 500ms wait)  (Atomic $gte stock check & update)
                         │
                  Kafka Event: order-placed
                         │
                         ▼
                     [ Apache Kafka Cluster ]
                         │
                         ▼
                     [ Module 2: processing-service ]
                         ├─────────────────────────────────────────┐
                         │                                         │
                         ▼                                         ▼
                 [ Payment Worker ]                        [ Shipping Worker ]
             (Group: payment-workers)                   (Group: shipping-workers)
              • Happy path: PAYMENT_PROCESSED            • Generates tracking ID
              • Emits: order-payment-processed           • Updates status: SHIPPED
              • Failure path (prod-fail-payment or 99):  • Emits: order-shipped
                - Updates status: FAILED
                - Emits: order-payment-failed
                         │
                         ▼
             [ Compensation Consumer ]
             (Group: compensation-workers)
              • Acquires Redis lock: lock:inventory:{productId}
              • Restores stock in MongoDB: $inc: { stock: +qty }
              • Logs compensation in order statusHistory
```

---

## Architectural & Design Decision Rationale (Interview Talking Points)

### 1. Why Redis Distributed Lock over Database Pessimistic Locking (`SELECT ... FOR UPDATE`)
- **Connection Pool Exhaustion & Row Contention:** In high-concurrency flash sales, database pessimistic locks hold open relational database connections and transactions for the duration of stock checks, external calls, and entity writes. Under heavy traffic (e.g. 50+ concurrent buyers for 1 item), database connection pools quickly saturate, leading to thread starvation and system-wide latency degradation.
- **Granular Partitioning:** The Redis lock partitions contention strictly per product using the key format `lock:inventory:{productId}`. Purchases for Product B are completely unaffected by a thundering herd on Product A.
- **Microsecond In-Memory Execution:** Redis executes in-memory with sub-millisecond roundtrips. Threads acquire or fail the lock within a 500ms wait window (`tryLock(500, 3000, TimeUnit.MILLISECONDS)`), serializing access safely before touching MongoDB.
- **Automatic Deadlock Guard (TTL):** The 3000ms lease time ensures locks expire automatically if a JVM node crashes or suffers a network partition while holding a lock.
- **Defense-in-Depth:** The Redis lock is backed by an atomic MongoDB decrement guard (`stock >= quantity`), guaranteeing zero overselling even if a lock were forcibly evicted.

### 2. Why Apache Kafka over Direct Synchronous REST Calls
- **Temporal Decoupling & Availability:** Payment gateways and shipping courier APIs suffer latency spikes and intermittent outages. If `order-service` made synchronous HTTP calls to Payment and Shipping providers during order placement, checkout latency would be 3–5+ seconds, and any gateway downtime would immediately fail client orders.
- **Immediate User Acknowledgment:** With Kafka, `order-service` reserves inventory, creates the order in initial state `PLACED`, and returns HTTP `201 Created` within ~20ms. Downstream processing occurs asynchronously in workers.
- **Independent Elastic Scaling:** `payment-workers` and `shipping-workers` run in isolated consumer groups. If shipping dispatch is slow, payment processing continues unimpeded, and workers can scale horizontally by adding Kafka consumer partitions.
- **Built-in Replayability & Dead Lettering:** In the event of temporary downstream crashes, Kafka consumer offsets guarantee no lost events, allowing safe retry upon recovery.

### 3. Idempotency & Replay Protection
- **SHA-256 Payload Fingerprinting:** Clients supply an `Idempotency-Key` header (UUID). `IdempotencyService` computes a SHA-256 hash of `productId + ":" + quantity`.
- **Duplicate Safe Returns:** If a completed request is resent with the identical payload (e.g. user refreshed the page or network timed out), the cached HTTP 201 response is returned without re-decrementing stock.
- **Payload Tampering Detection:** If a client reuses an idempotency key with modified product or quantity parameters, the service detects the hash mismatch and immediately rejects the call with HTTP **`422 Unprocessable Entity`** (`IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`).
- **Concurrent Request Gate:** If a duplicate arrives while the first request is still `IN_PROGRESS`, it returns HTTP **`409 Conflict`** (`ORDER_ALREADY_PROCESSING`).

---

## Project Structure

```
Distributed order processing/
├── docker-compose.yml                  # MongoDB, Redis, Zookeeper, Kafka, Microservices, Frontend
├── BUILD_ORDER.md                      # Hour-by-hour implementation schedule
├── WALKTHROUGH.md                      # Complete file-by-file codebase walkthrough
├── README.md                           # Architecture and operations guide
│
├── order-service/                      # Module 1: Spring Boot REST API & Order Intake (Port 8080)
│   ├── src/main/java/com/distributed/orderservice/
│   │   ├── config/                     # RedissonConfig, KafkaProducerConfig, MongoConfig, WebCorsConfig
│   │   ├── controller/                 # OrderController, ProductController
│   │   ├── service/                    # OrderService, InventoryLockService, IdempotencyService
│   │   ├── repository/                 # ProductRepository, OrderRepository, IdempotencyKeyRepository
│   │   ├── model/                      # Product, Order, IdempotencyRecord, OrderStatus
│   │   └── exception/                  # GlobalExceptionHandler, domain exceptions
│   └── src/test/java/com/distributed/orderservice/
│       ├── OrderServiceConcurrencyTest.java          # 50-thread concurrent race condition test
│       ├── IdempotencyServiceUnitTest.java           # Caching, in-progress 409, and payload mismatch 422
│       ├── OrderServiceUnitTest.java                 # Business logic, stock bounds, lock failures
│       └── OrderProcessingEndToEndIntegrationTest.java # Testcontainers (Mongo, Redis, Kafka)
│
├── processing-service/                 # Module 2: Kafka Consumers & Saga Workers (Port 8081)
│   ├── src/main/java/com/distributed/processingservice/
│   │   ├── config/                     # KafkaConfig, RedissonConfig
│   │   ├── consumer/                   # PaymentConsumer, ShippingConsumer, CompensationConsumer
│   │   ├── service/                    # PaymentProcessor, ShippingProcessor, InventoryCompensationService
│   │   ├── producer/                   # ProcessingEventPublisher
│   │   └── repository/                 # OrderRepository, ProductRepository
│   └── src/test/java/com/distributed/processingservice/
│       ├── PaymentProcessorUnitTest.java             # Payment success & deterministic failure trigger
│       ├── ShippingProcessorUnitTest.java            # Shipping dispatch & tracking assignment
│       └── CompensationConsumerUnitTest.java         # Saga stock compensation under Redis lock
│
└── frontend/                           # Module 3: React Vite Frontend (Port 3000)
    ├── src/
    │   ├── api.js                      # Fetch client targeting real backend at localhost:8080 (No Mocks)
    │   ├── components/
    │   │   ├── ProductList.jsx         # Screen 1: Product catalog, live stock badges, scenario tags
    │   │   ├── PlaceOrder.jsx          # Screen 2: Order form + 10-Buyer Concurrent Race Simulator
    │   │   └── OrderStatus.jsx         # Screen 3: Visual Kafka pipeline stepper & status audit log
    │   ├── App.jsx                     # Top navigation, tab switching, and state coordination
    │   └── index.css                   # Responsive styling, scoreboard, pulsing stepper, timeline
    ├── vite.config.js                  # Port 3000, proxy to 8080
    └── package.json
```

---

## Seed Data for Demonstrations

Seeded automatically by `MongoConfig` in `order_processing_db.products`:

| Product Name | Price | Stock | Demo Scenario |
|---|---|---|---|
| **Sony WH-1000XM5 Headphones** | $349.99 | **1** | **Race Condition Demo:** Low-stock item for concurrent buyer burst |
| **Keychron K2 Mechanical Keyboard** | $89.99 | **10** | **Happy Path Demo:** Healthy inventory for standard ordering |
| **Apple MacBook Pro M3** | $1999.99 | **5** | **Healthy Stock:** Standard multi-unit ordering |
| **Dell UltraSharp 27 4K Monitor** | $499.99 | **0** | **Out-of-Stock Demo:** Immediate rejection validation |
| **Simulated Payment Fail Item** (`prod-fail-payment`) | $99.99 | **5** | **Compensation Demo:** Hardcoded deterministic payment failure trigger |

---

## How to Run the Application

### Option A: Using Docker Compose (Full Stack)

```bash
# Start all infrastructure, backend services, and frontend in one command:
docker compose up --build
```
- Frontend UI: `http://localhost:3000`
- Order Service REST API: `http://localhost:8080`
- Processing Service: `http://localhost:8081`

### Option B: Running Locally (Development Mode)

#### 1. Start Infrastructure (MongoDB, Redis, Kafka)
```bash
docker compose up mongo redis zookeeper kafka -d
```

#### 2. Run `order-service`
```bash
cd order-service
.\mvnw.cmd spring-boot:run
```

#### 3. Run `processing-service`
```bash
cd processing-service
.\mvnw.cmd spring-boot:run
```

#### 4. Run `frontend`
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:3000` in your browser.

---

## Running the Automated Test Suite

### 1. `order-service` Tests (10 tests, 0 failures)
```bash
cd order-service
.\mvnw.cmd test -Dtest="OrderServiceUnitTest,IdempotencyServiceUnitTest,OrderServiceConcurrencyTest"
```
**Features Tested:**
- `testConcurrentOrderPlacement_RaceConditionGuard`: 50 concurrent threads targeting 1 stock item.
  - Assertions: Exactly 1 success (201), 49 failures (409), final stock = 0, total orders = 1.
- `testIdempotency_DuplicateKeyReturnsCachedResponse`: Cached 201 return.
- `testIdempotency_ReplayWithMismatchedPayloadReturns422`: HTTP 422 on payload tampering.
- `testIdempotency_ConcurrentInProgressReturns409`: HTTP 409 on duplicate in-flight requests.
- `testPlaceOrder_OutOfStock` & `testPlaceOrder_LockAcquisitionFailed`: Rejection mapping to 409.

### 2. `processing-service` Tests (5 tests, 0 failures)
```bash
cd processing-service
.\mvnw.cmd test
```
**Features Tested:**
- `testPaymentProcessing_Success`: Event consumption and transition to `PAYMENT_PROCESSED`.
- `testPaymentProcessing_DeterministicFailureTrigger_ByProductId`: `prod-fail-payment` triggers `FAILED` state and compensation event.
- `testPaymentProcessing_DeterministicFailureTrigger_ByQuantity`: `quantity=99` triggers `FAILED` state.
- `testShippingProcessing_Success`: Transition to `SHIPPED` with generated tracking number.
- `testCompensation_ReplenishesStockUnderRedisLock`: Restores inventory in MongoDB under Redis lock.

### 3. Testcontainers End-to-End Integration Test
```bash
cd order-service
.\mvnw.cmd test -Dtest="OrderProcessingEndToEndIntegrationTest"
```
*Spins up real MongoDB, Redis, and Kafka containers via Testcontainers to verify end-to-end event flow and status transition history.*

---

## Frontend 3-Screen Live Demo Walkthrough

1. **Screen 1: Product List**
   - Displays real-time catalog from `GET /api/products`.
   - Color-coded stock badges (Green > 3, Amber 1–3, Red = 0).
   - "Order / Test Race" button pre-selects product and switches to Screen 2.
2. **Screen 2: Place Order & Concurrent Race Simulator**
   - **Single Order:** Select product, quantity, manage Idempotency Key (UUID). Successful order placement generates Order ID and offers instant link to Screen 3.
   - **Simulate Concurrent Buyers (Interview Demo):**
     - Select `"Sony WH-1000XM5 Headphones"` (Stock: 1).
     - Set buyers $N = 10$ and click **"Blast 10 Concurrent Buyers"**.
     - Scoreboard immediately updates: **Total Fired: 10**, **Succeeded: 1 (Green)**, **Failed: 9 (Red)**.
     - Live breakdown feed displays Buyer #1: `201 Created` vs Buyers #2–10: `409 Conflict: OUT_OF_STOCK`.
3. **Screen 3: Order Status & Kafka Pipeline Stepper**
   - Given an Order ID, polls `GET /api/orders/{orderId}` every 1.5 seconds.
   - Visual Stepper transitions live:
     `[ 1. PLACED ] ──> [ 2. PAYMENT_PROCESSED ] ──> [ 3. SHIPPED ]`
   - Active stages pulse blue, completed stages turn green.
   - Chronological audit log below the stepper displays real-time transition timestamps committed to MongoDB `statusHistory`.
