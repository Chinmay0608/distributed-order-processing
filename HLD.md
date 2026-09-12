# High-Level Design (HLD): Distributed Order Processing System

---

## 1. Problem Statement

In modern e-commerce systems, high-contention flash sales present two fundamental distributed systems challenges:

1. **Inventory Overselling Under Concurrency:** When thousands of concurrent buyers attempt to purchase the final unit of a scarce item simultaneously, concurrent database transactions can experience race conditions (read-modify-write anomalies). This leads to overselling, negative stock levels, and costly customer cancellations.
2. **Synchronous Latency Bottlenecks:** Fulfilling an order requires orchestrating multiple time-consuming operations: payment gateway capture (1–3 seconds), inventory commit, warehouse logistics allocation, and notification delivery. Forcing the client HTTP thread to wait synchronously across this entire sequence degrades checkout latency, creates thread pool exhaustion, and introduces catastrophic cascading failures if downstream third-party gateways degrade.

**What This System Solves:**  
This system guarantees **zero overselling** under extreme burst contention through a multi-tier concurrency guard (distributed lock + atomic database check). It decouples synchronous order acceptance from asynchronous downstream execution via an event-driven streaming pipeline (Apache Kafka), returning an immediate `201 Created` status to the customer in under 150ms while completing payment processing and shipment dispatch asynchronously with automated compensation saga recovery upon failure.

---

## 2. Requirements Gathering

### Functional Requirements

| Requirement ID | Description | Primary Component |
| :--- | :--- | :--- |
| **FR-01** | **Synchronous Order Ingestion:** Accept order requests containing `productId` and `quantity`. | `OrderService.java` |
| **FR-02** | **Idempotent Order Submission:** Prevent duplicate order charges from network retries using an `Idempotency-Key` header and payload SHA-256 verification. | `IdempotencyService.java` |
| **FR-03** | **Zero-Oversell Inventory Reservation:** Guarantee stock is never decremented below zero regardless of concurrency level. | `InventoryLockService.java` + MongoDB `$gte` guard |
| **FR-04** | **Asynchronous Event Streaming:** Publish an `OrderPlacedEvent` to Kafka immediately after stock reservation to trigger background fulfillment. | `KafkaEventPublisher.java` |
| **FR-05** | **Decoupled Payment Processing:** Consume placed orders, simulate payment capture latency (1500ms), and transition status to `PAYMENT_PROCESSED` or `FAILED`. | `PaymentConsumer.java`, `PaymentProcessor.java` |
| **FR-06** | **Fulfillment & Shipping Dispatch:** Consume approved payments, generate a courier tracking number, and transition status to `SHIPPED`. | `ShippingConsumer.java`, `ShippingProcessor.java` |
| **FR-07** | **Compensating Inventory Saga:** If payment capture fails, consume `order-payment-failed`, re-acquire the distributed inventory lock, and restore stock in MongoDB. | `CompensationConsumer.java`, `InventoryCompensationService.java` |
| **FR-08** | **Order Status & Audit Tracking:** Provide real-time order lookup and immutable state transition history. | `OrderController.java` (`GET /api/orders/{orderId}`) |

---

### Non-Functional Requirements

| Requirement ID | Target Metric | Architectural Mechanism |
| :--- | :--- | :--- |
| **NFR-01: Correctness** | Zero overselling; strict atomic stock deduction ($Stock \ge 0$ invariant). | Redisson distributed lock + MongoDB atomic conditional `$inc: -quantity`. |
| **NFR-02: Availability** | Fail-fast under high contention; eliminate thread pool starvation. | 500ms Redis lock wait timeout (`tryLock`) shedding excess contention to HTTP 409. |
| **NFR-03: Low Ingestion Latency** | P99 order ingestion response time $< 200\text{ ms}$. | Downstream operations (payment & shipping) offloaded asynchronously to Kafka. |
| **NFR-04: Idempotency Integrity** | Identical response returned on replay; reject parameter mutations with HTTP 422. | MongoDB unique index on `idempotency_keys._id` + SHA-256 payload hash validation. |
| **NFR-05: Fault Tolerance** | Consumer failures do not lose orders or corrupt data. | Kafka persistent log with committed offsets + Redisson distributed lock on compensation. |

---

### Out of Scope

1. **Real Payment Gateway Integration:** Real Stripe/PayPal banking APIs and PCI-DSS tokenization are omitted; replaced with deterministic simulation logic.
2. **User Authentication & Authorization:** OAuth2/JWT session verification is omitted to focus purely on distributed data integrity.
3. **Multi-Region Active-Active Replication:** Single-region cluster assumed; cross-datacenter WAN Redis/Mongo consensus is excluded.
4. **Customer Returns & Refund Sagas:** Post-shipment return workflows and reverse logistics pipelines are excluded.

---

### Back-of-the-Envelope Scale Estimation

- **Daily Baseline Volume:** 10,000 orders/day $\approx 0.12\text{ orders/sec}$ average throughput.
- **Flash-Sale Burst Contention:** A hot SKU launch where 50 to 500 concurrent buyers blast the ingestion gateway within a 1-second window on a single product with stock = 1.
- **Read/Write Ratio:**
  - Catalog browsing: Read-heavy (10:1 read-to-write ratio).
  - Checkout execution: Write-intensive (locking, database conditional update, event emission).
- **Storage Projections:**
  - Order record: ~1 KB.
  - 10,000 orders/day $\times 1\text{ KB} = 10\text{ MB/day} \approx 3.65\text{ GB/year}$ (well within single-node MongoDB capacity).
  - Idempotency records: TTL-indexed to expire after 24 hours (`86400` seconds), bounding collection size.

---

## 3. High-Level Architecture Diagram

```
                                  +---------------------------------------+
                                  |           Browser / Client            |
                                  | (React Vite Dashboard / Locust Tests) |
                                  +-------------------+-------------------+
                                                      |
                                                      | HTTP /api/orders (Idempotency-Key)
                                                      v
                                  +---------------------------------------+
                                  |        ORDER SERVICE (Port 8080)      |
                                  |   - Input Validation                  |
                                  |   - Idempotency Check (SHA-256)       |
                                  |   - Redis Distributed Lock Guard      |
                                  |   - Mongo Atomic Stock Decrement      |
                                  |   - Order Document Persistence        |
                                  |   - Kafka Event Emission              |
                                  +---------+-------------------+---------+
                                            |                   |
                     +----------------------+                   +----------------------+
                     | Redis Lock & Cache                       | Document Writes      | Kafka Topic Publish
                     v                                          v                      v
           +--------------------+                     +--------------------+   +-----------------------+
           |       REDIS        |                     |      MONGODB       |   |      APACHE KAFKA     |
           |  (Port 6379)       |                     |    (Port 27017)    |   |     (Port 9092)       |
           | - lock:inventory:* |                     | - products         |   | - order-placed        |
           | - Redisson Pub/Sub |                     | - orders           |   | - order-payment-proc  |
           +---------^----------+                     | - idempotency_keys |   | - order-payment-failed|
                     |                                +---------^----------+   | - order-shipped       |
                     |                                          |              +-----------+-----------+
                     | Lock Re-Acquire                          | State Update             |
                     | for Compensation                         | & Stock Inc              | Consumer Groups
                     |                                          |                          |
                     +---------------------+                    |                          v
                                           |                    |             +-------------------------+
                                           |                    |             |    PROCESSING SERVICE   |
                                           |                    |             |       (Port 8081)       |
                                           |                    |             | - PaymentConsumer       |
                                           |                    |             | - PaymentProcessor      |
                                           |                    |             | - ShippingConsumer      |
                                           |                    +-------------+ - ShippingProcessor     |
                                           +----------------------------------+ - CompensationConsumer  |
                                                                              | - InventoryCompService  |
                                                                              +-------------------------+
```

### Architectural Rationale: Two Services vs. One Monolith

The system is split into two independent Spring Boot microservices (`order-service` and `processing-service`):

1. **Failure Domain Isolation:** Order ingestion is mission-critical. If the payment gateway, shipping carrier API, or background processing logic experiences high latency, high memory usage, or fatal exceptions, the `order-service` HTTP ingestion threads remain completely unaffected and continue serving checkout requests.
2. **Asymmetric Resource Scaling:** `order-service` is I/O-bound (low CPU, high network concurrency, fast Redis/Mongo transactions $< 100\text{ ms}$). `processing-service` is worker-bound (simulated gateway latency of 1500ms, external partner retries). Scaling `order-service` requires more HTTP web worker threads, whereas scaling `processing-service` requires more Kafka consumer worker threads across partitions.
3. **Graceful Backpressure Management:** Under extreme flash-sale spikes, `order-service` ingests orders into the durable Kafka topic `order-placed`. Kafka absorbs the incoming traffic spike as a shock absorber. The `processing-service` pulls events at its own sustainable processing rate without dropping orders.

---

## 4. Component Deep Dive

### 1. Order Service (`com.distributed.orderservice`)
- **`OrderController.java`:** Exposes `POST /api/orders`, `GET /api/orders/{orderId}`, and `GET /api/products`.
- **`OrderService.java`:** Orchestrates the complete 10-step atomic place-order transaction.
- **`InventoryLockService.java`:** Redisson distributed locking facade managing acquisition, wait timeouts, lease watchdog TTLs, and thread-safe lock releases.
- **`IdempotencyService.java`:** Validates incoming `Idempotency-Key` headers, computes request payload SHA-256 hashes, prevents concurrent in-flight execution, and records cached response bodies.
- **`KafkaEventPublisher.java`:** Publishes `OrderPlacedEvent` records to Kafka topic `order-placed` keyed by `orderId`.
- **`GlobalExceptionHandler.java`:** Centralized error mapper translating domain exceptions into standard HTTP status codes (`400`, `404`, `409`, `422`, `500`).

### 2. Processing Service (`com.distributed.processingservice`)
- **`PaymentConsumer.java`:** Listens to topic `order-placed` under consumer group `payment-workers`.
- **`PaymentProcessor.java`:** Simulates payment gateway latency (`simulation.delay-ms = 1500`) and evaluates deterministic failure conditions (`productId == "prod-fail-payment"` or `quantity == 99`). Updates MongoDB order state to `PAYMENT_PROCESSED` or `FAILED`.
- **`ShippingConsumer.java`:** Listens to topic `order-payment-processed` under consumer group `shipping-workers`.
- **`ShippingProcessor.java`:** Simulates logistics carrier latency (1500ms), generates courier tracking numbers (`TRK-XXXXXXXX`), transitions order state to `SHIPPED`, and emits `order-shipped`.
- **`CompensationConsumer.java`:** Listens to topic `order-payment-failed` under consumer group `compensation-workers`.
- **`InventoryCompensationService.java`:** Re-acquires the Redisson distributed lock (`lock:inventory:{productId}`), atomically increments stock in MongoDB via `$inc: +quantity`, and logs audit entries.

### 3. Redis (`redis:7.2-alpine`)
- **Redisson Distributed Lock:** Provides distributed mutual exclusion via Redis hash-based Lua scripts with automatic renewal.
- **Lock Key Structure:** `lock:inventory:{productId}`.
- **Contention Load-Shedding:** Enforces a 500ms wait timeout (`WAIT_TIME_MS = 500`) and a 3000ms watchdog lease time (`LEASE_TIME_MS = 3000`).

### 4. MongoDB (`mongo:7.0`)
- **System of Record:** Stores product inventory, customer orders, and idempotency states.
- **Conditional Atomic Execution:** Utilizes MongoDB's document-level atomic conditional update (`updateFirst` with `stock >= quantity` and `$inc: -quantity`) as the final consistency barrier.
- **Audit Trails:** Embeds chronological status transition histories directly within the order document.

### 5. Apache Kafka (`cp-kafka:7.5.0` with ZooKeeper)
- **Durable Event Backbone:** Decouples order acceptance from multi-stage fulfillment.
- **Pre-Configured Topics:** `order-placed`, `order-payment-processed`, `order-payment-failed`, `order-shipped` (each configured with 3 partitions and replication factor 1).
- **Ordering Guarantee:** All events are partitioned by `orderId`, guaranteeing sequential processing for each specific order.

---

## 5. Data Model

### MongoDB Document Schemas

#### 1. Products Collection (`products`)
*Defined in [`Product.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/model/Product.java)*

```json
{
  "_id": "6aa4d664c4a7835bc162201d",
  "name": "Sony WH-1000XM5 Headphones",
  "description": "Wireless Noise Cancelling Headphones (Single-Unit Inventory)",
  "price": 349.99,
  "stock": 1,
  "createdAt": "2026-09-12T04:00:00.000Z",
  "updatedAt": "2026-09-12T04:00:00.000Z"
}
```

#### 2. Orders Collection (`orders`)
*Defined in [`Order.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/model/Order.java)*

```json
{
  "_id": "6aa4e112c4a7835bc1622020",
  "orderId": "ORD-7E4A10B2",
  "idempotencyKey": "8e8a20d8-2f88-4599-8244-c083d2ac1661",
  "productId": "6aa4d664c4a7835bc162201d",
  "productName": "Sony WH-1000XM5 Headphones",
  "quantity": 1,
  "unitPrice": 349.99,
  "totalAmount": 349.99,
  "status": "SHIPPED",
  "statusHistory": [
    {
      "status": "PLACED",
      "timestamp": "2026-09-12T05:00:00.120Z",
      "detail": "Order placed and stock reserved"
    },
    {
      "status": "PAYMENT_PROCESSED",
      "timestamp": "2026-09-12T05:00:01.650Z",
      "detail": "Payment captured successfully via gateway"
    },
    {
      "status": "SHIPPED",
      "timestamp": "2026-09-12T05:00:03.180Z",
      "detail": "Order dispatched via courier (Tracking: TRK-5B9F2A10)"
    }
  ],
  "createdAt": "2026-09-12T05:00:00.120Z",
  "updatedAt": "2026-09-12T05:00:03.180Z"
}
```
*Indexes:*
- `orderId`: Unique index.
- `idempotencyKey`: Unique sparse index.
- `productId`, `status`: Single-field lookup indexes.

#### 3. Idempotency Records Collection (`idempotency_keys`)
*Defined in [`IdempotencyRecord.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/model/IdempotencyRecord.java)*

```json
{
  "_id": "8e8a20d8-2f88-4599-8244-c083d2ac1661",
  "status": "COMPLETED",
  "requestPayloadHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "orderId": "ORD-7E4A10B2",
  "responseStatusCode": 201,
  "responseBody": {
    "orderId": "ORD-7E4A10B2",
    "idempotencyKey": "8e8a20d8-2f88-4599-8244-c083d2ac1661",
    "productId": "6aa4d664c4a7835bc162201d",
    "productName": "Sony WH-1000XM5 Headphones",
    "quantity": 1,
    "totalAmount": 349.99,
    "status": "PLACED",
    "createdAt": "2026-09-12T05:00:00.120Z"
  },
  "createdAt": "2026-09-12T05:00:00.080Z"
}
```
*Indexes:*
- `_id`: Primary key (idempotency key string).
- `createdAt`: TTL index (`expireAfterSeconds = 86400`), automatically purged after 24 hours.

### Justification of MongoDB Over Relational Database
> MongoDB was chosen because order status progression is intrinsically document-centric: appending audit events into an embedded array (`statusHistory`) and caching variable JSON response payloads in `idempotency_keys` can be executed within a single document write without expensive relational table joins, while MongoDB's document-level conditional `$inc` provides the exact atomic Compare-And-Set guarantee required for zero-oversell stock decrements.

---

## 6. API Design

### Implemented REST Endpoints

| HTTP Method | Endpoint URI | Headers | Request Body | Success Response | Error Codes & Error Types |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`GET`** | `/api/products` | None | None | `200 OK`<br>`List<ProductResponse>` | `500 INTERNAL_ERROR` |
| **`POST`** | `/api/orders` | `Idempotency-Key: <UUID>` *(Required)* | `{"productId": "...", "quantity": 1}` | `201 Created`<br>`OrderResponse` | `400 MISSING_IDEMPOTENCY_KEY`<br>`400 INVALID_INPUT`<br>`404 NOT_FOUND`<br>`409 OUT_OF_STOCK`<br>`409 LOCK_ACQUISITION_FAILED`<br>`409 ORDER_ALREADY_PROCESSING`<br>`422 IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`<br>`500 INTERNAL_ERROR` |
| **`GET`** | `/api/orders/{orderId}` | None | None | `200 OK`<br>`OrderStatusResponse` | `404 NOT_FOUND`<br>`500 INTERNAL_ERROR` |

### Exception Mapping (`GlobalExceptionHandler.java`)

```
Domain Exception                       HTTP Status                     JSON error field
---------------------------------------------------------------------------------------------
OutOfStockException               -->  409 CONFLICT               -->  "OUT_OF_STOCK"
LockAcquisitionException          -->  409 CONFLICT               -->  "LOCK_ACQUISITION_FAILED"
OrderAlreadyProcessingException   -->  409 CONFLICT               -->  "ORDER_ALREADY_PROCESSING"
IdempotencyPayloadMismatchException->  422 UNPROCESSABLE ENTITY   -->  "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH"
ResourceNotFoundException         -->  404 NOT_FOUND              -->  "NOT_FOUND"
MissingRequestHeaderException     -->  400 BAD REQUEST            -->  "MISSING_IDEMPOTENCY_KEY"
MethodArgumentNotValidException   -->  400 BAD REQUEST            -->  "INVALID_INPUT"
IllegalArgumentException          -->  400 BAD REQUEST            -->  "INVALID_INPUT"
Exception (General)               -->  500 INTERNAL SERVER ERROR  -->  "INTERNAL_ERROR"
```

---

## 7. Deep-Dive Flow 1: Place Order Transaction

### ASCII Sequence Diagram

```
 Client             OrderService          Redis (Lock)           MongoDB            Kafka
   |                     |                     |                     |                |
   | 1. POST /orders     |                     |                     |                |
   |-------------------->|                     |                     |                |
   |                     | 2. Check Idempotency|                     |                |
   |                     |    & SHA-256 Hash   |                     |                |
   |                     |------------------------------------------>|                |
   |                     |                     |                     | (idempotency_  |
   |                     |                     |                     |  keys insert)  |
   |                     | 3. acquireLock()    |                     |                |
   |                     |    tryLock(500ms)   |                     |                |
   |                     |-------------------->|                     |                |
   |                     |    Lock Acquired    |                     |                |
   |                     |<--------------------|                     |                |
   |                     |                     |                     |                |
   |                     | 4. findById()       |                     |                |
   |                     |------------------------------------------>|                |
   |                     |    Check stock >= quantity                |                |
   |                     |                                           |                |
   |                     | 5. updateFirst() atomic decrement         |                |
   |                     |    Criteria: _id == id && stock >= qty    |                |
   |                     |    Update: $inc: -qty                     |                |
   |                     |------------------------------------------>|                |
   |                     |    modifiedCount == 1                     |                |
   |                     |<------------------------------------------|                |
   |                     |                                           |                |
   |                     | 6. orderRepository.save(order)            |                |
   |                     |------------------------------------------>|                |
   |                     |                                           |                |
   |                     | 7. releaseLock() in finally block         |                |
   |                     |-------------------->|                     |                |
   |                     |                                           |                |
   |                     | 8. markCompleted(response)                |                |
   |                     |------------------------------------------>|                |
   |                     |                                           |                |
   |                     | 9. publishOrderPlaced(event)              |                |
   |                     |----------------------------------------------------------->|
   |                     |                                           |                |
   | 10. HTTP 201 Created|                                           |                |
   |<--------------------|                                           |                |
```

### Complete Failure Branch Matrix (Step-by-Step)

1. **Step 1 Failure (Header/Input Validation):** Missing `Idempotency-Key` or `quantity <= 0`.  
   *Result:* Request aborted immediately. Returns `400 BAD_REQUEST` (`MISSING_IDEMPOTENCY_KEY` or `INVALID_INPUT`). Zero database hits.
2. **Step 2A Failure (Concurrent In-Flight Duplicate):** Idempotency key already exists in state `IN_PROGRESS` or MongoDB unique index throws `DuplicateKeyException`.  
   *Result:* Throws `OrderAlreadyProcessingException`. Returns `409 CONFLICT` (`ORDER_ALREADY_PROCESSING`).
3. **Step 2B Failure (Mismatched Replay Attack):** Idempotency key exists in state `COMPLETED`, but incoming request's SHA-256 hash does not match `requestPayloadHash`.  
   *Result:* Throws `IdempotencyPayloadMismatchException`. Returns `422 UNPROCESSABLE_ENTITY` (`IDEMPOTENCY_KEY_PAYLOAD_MISMATCH`).
4. **Step 2C Success (Cached Response Replay):** Idempotency key exists in state `COMPLETED` and SHA-256 hash matches.  
   *Result:* Short-circuits entire transaction. Returns cached `201 Created` with original order body. Zero lock contention, zero inventory decrement.
5. **Step 3 Failure (Lock Contention Timeout):** Lock not acquired within 500ms (`tryLock(500, 3000, TimeUnit.MILLISECONDS)` returns `false`).  
   *Result:* Throws `LockAcquisitionException`. Marks idempotency record as `FAILED`. Returns `409 CONFLICT` (`LOCK_ACQUISITION_FAILED`). Database stock is never touched.
6. **Step 4 Failure (Product Missing):** Product ID does not exist in MongoDB.  
   *Result:* Throws `ResourceNotFoundException`. Returns `404 NOT_FOUND`. Lock safely released in `finally`.
7. **Step 5A Failure (Pre-Check Out of Stock):** Document `stock < quantity`.  
   *Result:* Throws `OutOfStockException`. Returns `409 CONFLICT` (`OUT_OF_STOCK`). Lock safely released in `finally`.
8. **Step 5B Failure (Atomic Guard Out of Stock):** Concurrent decrement race results in `updateResult.getModifiedCount() == 0`.  
   *Result:* Throws `OutOfStockException`. Returns `409 CONFLICT` (`OUT_OF_STOCK`). Lock safely released in `finally`.
9. **Step 6 Failure (Order Insert Error):** MongoDB fails while inserting the `orders` document.  
   *Result:* Catch-block executes immediate local compensation: `mongoTemplate.updateFirst(..., new Update().inc("stock", quantity))` to revert the decremented stock, marks idempotency `FAILED`, and rethrows. Lock released in `finally`.
10. **Step 7 Guarantee:** Lock is **always released** inside `finally { inventoryLockService.releaseLock(lock); }` ensuring locks are never leaked even during unexpected runtime exceptions.

---

## 8. Deep-Dive Flow 2: Concurrency Control

### The Two-Gate Concurrency Defense Model

```
Incoming Concurrent Requests (e.g. 50 simultaneous buyers on stock = 1)
                              │
                              ▼
        +───────────────────────────────────────────+
        |   GATE 1: Redis Redisson Distributed Lock |
        |   Key: lock:inventory:{productId}         |
        |   Wait Time: 500ms  |  TTL: 3000ms        |
        +─────────────────────┬─────────────────────+
                              │
            ┌─────────────────┴─────────────────┐
            │ Lock timeout (>500ms)             │ Lock acquired
            ▼                                   ▼
+───────────────────────+          +──────────────────────────────────────────+
| 409 CONFLICT          |          |  GATE 2: MongoDB Atomic Conditional CAS  |
| LOCK_ACQUISITION_FAIL |          |  Filter: _id == id AND stock >= quantity |
| (Load Shedding Gate:  |          |  Update: $inc: -quantity                 |
|  Protects Tomcat & DB)|          +────────────────────┬─────────────────────+
+───────────────────────+                               │
                                      ┌─────────────────┴─────────────────┐
                                      │ modifiedCount == 0                │ modifiedCount == 1
                                      ▼                                   ▼
                          +───────────────────────+          +────────────────────────+
                          | 409 CONFLICT          |          | 201 CREATED            |
                          | OUT_OF_STOCK          |          | Order Created & Stock  |
                          | (Inventory Integrity  |          | Allocated (Winner)     |
                          |  Gate: 0 Oversell)    |          +────────────────────────+
                          +───────────────────────+
```

### Exact Configuration Parameters (`InventoryLockService.java`)
- **`LOCK_PREFIX`:** `"lock:inventory:"`
- **Lock Name:** `"lock:inventory:" + productId`
- **`WAIT_TIME_MS`:** `500` ms (maximum thread wait window before shedding contention)
- **`LEASE_TIME_MS`:** `3000` ms (automatic release watchdog ensuring no deadlocks if an app node crashes)

---

### Observed Concurrency Test Results: Unit Test vs. Live Browser

#### 1. In-JVM Mock Test (`OrderServiceConcurrencyTest.java`)
- **Setup:** 50 concurrent threads spawned via `Executors.newFixedThreadPool(50)` synchronized by `CountDownLatch(1)`. Target: SKU with stock = 1.
- **Assertion Code:**
  ```java
  assertEquals(1, successfulResponses.size(), "Exactly 1 concurrent buyer must succeed");
  assertEquals(49, failedResponses.size(), "Exactly 49 concurrent buyers must fail");
  assertEquals(0, currentStock.get(), "Final stock must be exactly 0");
  assertEquals(1, totalOrdersCreated.get(), "Total orders saved must be exactly 1");
  ```
- **Console Log Output:**
  ```
  >>> Concurrency Test Breakdown: Succeeded=1, OutOfStock=49, LockTimeout=0, TotalFailures=49
  ```
- **Why `LockTimeout=0`:** In the mocked JVM test, in-memory operations execute in **~20 microseconds**. All 50 threads acquire and release the lock sequentially within ~1.5ms total elapsed time—vastly below the 500ms budget.

#### 2. Live Browser Test (10 Concurrent HTTP Requests via `Promise.allSettled`)
- **Setup:** 10 browser clients blasting `POST /api/orders` against Docker stack.
- **Observed Breakdown:**
  - Succeeded: `1` (HTTP 201 Created)
  - Buyer #2: `OUT_OF_STOCK` (HTTP 409 Conflict)
  - Buyers #3 through #10: `LOCK_ACQUISITION_FAILED` (HTTP 409 Conflict)
- **Why Live Contention Produces `LOCK_ACQUISITION_FAILED`:**
  In a real network environment, the winning buyer holds the lock for **~90ms–140ms** while executing real TCP network socket I/O: Redisson Lua scripts, MongoDB queries with disk journaling/write-concern, and Kafka metadata serialization.  
  As later threads queue in Redisson, their elapsed wait time exceeds the configured 500ms threshold ($4 \times 120\text{ms} = 480\text{ms}$). Redisson returns `false`, shedding the queued threads at the Redis layer.

**Conclusion:** Both failure modes guarantee **zero overselling**; Gate 1 acts as the **traffic cop** protecting thread pools, and Gate 2 acts as the **vault** protecting inventory.

---

## 9. Deep-Dive Flow 3: Asynchronous Order Pipeline (Kafka)

### Kafka Topic Choreography

```
+------------------+           order-placed            +--------------------+
|  Order Service   |---------------------------------->|  Payment Consumer  |
|  (Producer)      |         Partitions: 3             | (payment-workers)  |
+------------------+         Replication: 1            +---------+----------+
                                                                 |
                                 +-------------------------------+
                                 |
                                 v Evaluates gateway status
                                 |
        +------------------------+------------------------+
        | Payment Approved                                | Payment Declined
        v                                                 v
order-payment-processed                           order-payment-failed
  Partitions: 3                                     Partitions: 3
  Replication: 1                                    Replication: 1
        |                                                 |
        v                                                 v
+--------------------+                            +-----------------------+
| Shipping Consumer  |                            | Compensation Consumer |
| (shipping-workers) |                            | (compensation-workers)|
+---------+----------+                            +-----------+-----------+
          |                                                   |
          v Generates TRK-XXXXXXXX                            v Re-acquires Lock &
    order-shipped                                               $inc stock in Mongo
```

### Kafka Configuration Specifications

| Topic Name | Partitions | Replicas | Key Serializer | Value Serializer | Consumer Group ID |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `order-placed` | 3 | 1 | `StringSerializer` | `JsonSerializer` | `payment-workers` |
| `order-payment-processed` | 3 | 1 | `StringSerializer` | `JsonSerializer` | `shipping-workers` |
| `order-payment-failed` | 3 | 1 | `StringSerializer` | `JsonSerializer` | `compensation-workers` |
| `order-shipped` | 3 | 1 | `StringSerializer` | `JsonSerializer` | *(Telemetry / Notification)* |

### Why Asynchronous Event Streaming Over Direct REST Calls
1. **Temporal Decoupling:** Downstream service downtime does not fail the checkout flow. If shipping is down, orders accumulate safely in Kafka.
2. **Elimination of Distributed Deadlocks:** Direct synchronous RPC chains ($A \rightarrow B \rightarrow C$) tie up client HTTP threads and create circular dependency risks.
3. **Partition-Level Order Guarantees:** Partitioning by `orderId` ensures all state updates for a specific order are consumed sequentially without race conditions.

---

## 10. Deep-Dive Flow 4: Compensation Saga

When payment fails, the system executes an automated compensating transaction to undo the provisional stock reservation.

```
PaymentProcessor                          Kafka                               InventoryCompensationService
       |                                    |                                                |
       | 1. evaluateShouldFail() == true    |                                                |
       |    order.status = FAILED           |                                                |
       |----------------------------------->|                                                |
       |                                    |                                                |
       | 2. emit OrderPaymentFailedEvent    |                                                |
       |----------------------------------->|                                                |
       |                                    | 3. consumePaymentFailed()                      |
       |                                    |----------------------------------------------->|
       |                                    |                                                |
       |                                    |    4. acquireLock("lock:inventory:" + prodId)  |
       |                                    |    5. mongoTemplate.updateFirst(               |
       |                                    |         Query(_id == prodId),                  |
       |                                    |         Update().inc("stock", +quantity)       |
       |                                    |       )                                        |
       |                                    |    6. order.addStatusHistory(FAILED,           |
       |                                    |         "Inventory restored")                  |
       |                                    |    7. releaseLock() in finally block           |
```

### Deterministic Failure Triggers (`PaymentProcessor.java`)
Payment capture failure is triggered deterministically if either condition matches:
1. `event.getProductId().equals("prod-fail-payment")` (dedicated catalog compensation test SKU).
2. `event.getQuantity() == 99` (global failure trigger for any product).

### Compensation Safety Invariant
The compensation worker **re-acquires the Redisson distributed lock** (`lock:inventory:{productId}`) before executing the MongoDB `$inc: +quantity`. This prevents race conditions between compensating inventory restorations and incoming new orders.

---

## 11. Failure Mode Analysis

| Failure Scenario | Exact System Behavior as Implemented | Correctness Justification |
| :--- | :--- | :--- |
| **Simultaneous Flash-Sale Blast (Stock = 1)** | 1st buyer acquires lock & claims stock. 2nd buyer checks DB, sees stock = 0, gets `OUT_OF_STOCK`. Remaining buyers exceed 500ms lock wait, get `LOCK_ACQUISITION_FAILED`. | Exactly 1 winner. Zero overselling. Server resources protected against queue exhaustion. |
| **Network Client Retries POST with Same Key** | `IdempotencyService` detects `status == COMPLETED` in `idempotency_keys` with matching SHA-256 hash. | Returns original cached `OrderResponse` (HTTP 201) without deducting stock a second time. |
| **Malicious Payload Mutation with Reused Key** | `IdempotencyService` detects `status == COMPLETED` but computed SHA-256 payload hash differs from stored hash. | Aborts with `422 UNPROCESSABLE_ENTITY`. Prevents tampering with order quantities under existing keys. |
| **Two Simultaneous Requests with Same Key** | MongoDB unique index on `idempotency_keys._id` throws `DuplicateKeyException` on the second insert. | Second thread receives `409 CONFLICT` (`ORDER_ALREADY_PROCESSING`). Zero duplicate orders created. |
| **Server Crashes While Holding Redis Lock** | Redisson watchdog timer expires after `LEASE_TIME_MS = 3000` (3 seconds). | Redis automatically releases the key. No permanent system deadlock. |
| **Mongo Crashes During Order Save (Step 6)** | Catch block catches exception, executes `$inc: +quantity` rollback, marks idempotency `FAILED`. | Reverts decremented stock immediately. Lock released in `finally`. |
| **Payment Gateway Failure** | `PaymentProcessor` emits `OrderPaymentFailedEvent`. `CompensationConsumer` acquires Redis lock and restores stock. | Eventual consistency achieved: customer order marked `FAILED`, inventory replenished. |
| **Kafka Broker Temporarily Offline** | `KafkaEventPublisher` logs error asynchronously; order is committed in MongoDB and can be retried via outbox sweep. | Customer's order and stock reservation are safely preserved in primary database. |

---

## 12. Scaling Discussion

### 1. Scaling to 100,000 Orders/Second on One Hot SKU
At 100,000 ops/sec, acquiring a distributed lock that guards a multi-millisecond network database transaction will collapse due to lock contention ($100{,}000 \times 10\text{ms} = 1000\text{ seconds}$ of serialized queue delay).

**Required Architectural Evolutions:**
1. **In-Memory Redis Atomic Inventory Decrement:** Move stock checks entirely into Redis memory using an atomic Lua script:
   ```lua
   if redis.call('get', KEYS[1]) >= ARGV[1] then
       return redis.call('decrby', KEYS[1], ARGV[1])
   else
       return -1
   end
   ```
   Redis executes Lua scripts single-threaded in $< 0.1\text{ ms}$, handling 50,000–100,000 operations per second on a single instance without locks.
2. **Transactional Outbox Pattern:** Once Redis decrements memory stock, an order token is written to an Outbox table and streamed asynchronously to MongoDB via Kafka.
3. **Virtual Waiting Room / Rate Limiting:** Place Cloudflare Waiting Room or a Redis Token Bucket rate limiter in front of the ingestion gateway to reject or queue traffic before it reaches Tomcat.

### 2. Horizontal Scaling of Order Service
- `OrderService` is **100% stateless**. No sticky sessions or local memory state exist.
- Multiple instances can run behind an AWS ALB / NGINX reverse proxy.
- State coordination is delegated entirely to the external Redis cluster (locking) and MongoDB (persistence).

### 3. Scaling Kafka Consumers
- Topics currently have **3 partitions** (`partitions = 3`).
- In Kafka, **1 partition can only be actively consumed by 1 consumer thread per consumer group**.
- Therefore, each consumer group (`payment-workers`, `shipping-workers`, `compensation-workers`) can scale horizontally to a maximum of **3 parallel worker instances**.
- To scale workers to 30 instances, the topic partition count must be increased to 30:
  ```bash
  kafka-topics.sh --alter --topic order-placed --partitions 30 --bootstrap-server localhost:9092
  ```

---

## 13. Architectural Trade-Offs Matrix

| Architectural Decision | Alternative Considered | Why Chosen | Trade-Off Incurred |
| :--- | :--- | :--- | :--- |
| **Redis Lock + Mongo Atomic Check** | MongoDB Multi-Document Transactions alone | Redis lock serializes contention in memory and sheds excessive queue load early, preventing hundreds of threads from thrashing MongoDB transactions. | Requires running and operating a high-availability Redis cluster alongside MongoDB. |
| **500ms Lock Wait Timeout** | Long wait (5000ms) or Instant fail (0ms) | 500ms allows 3–4 legitimate sequential database transactions to clear without thread starvation under flash sales. | Under extreme contention, late buyers fail with `LOCK_ACQUISITION_FAILED` instead of reaching the database. |
| **Asynchronous Kafka Event Pipeline** | Synchronous REST Calls between services | Decouples checkout latency from slow third-party gateways; Kafka acts as an elastic buffer during traffic surges. | Eventual consistency: customer receives `201 Created` while payment is still in-flight. |
| **MongoDB Document Store** | PostgreSQL Relational DB | Native atomic conditional updates (`$inc`), embedded schema-free audit logging, and built-in TTL indexes for idempotency records. | Lack of native multi-collection cross-document ACID transactions across independent entities. |
| **Deterministic Payment Failures (`prod-fail-payment` / `qty=99`)** | Random percentage failure (`Math.random() < 0.2`) | Guarantees deterministic, reproducible test scenarios for automated CI/CD suites and end-to-end integration testing. | Does not simulate random non-deterministic network dropouts. |
