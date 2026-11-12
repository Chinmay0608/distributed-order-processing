# Engineering Case Study: High-Concurrency Flash Sale Inventory Overselling & Saga Dual-Commit Resolution

**System:** Distributed Order Processing Engine  
**Domain:** Distributed Systems / Event-Driven Microservices / High-Concurrency E-Commerce  
**Tech Stack:** Java 17, Spring Boot, Redis (Redisson), Apache Kafka, MongoDB, Docker  

---

## 1. Executive Summary & Context

In high-throughput e-commerce platforms during flash sale events, hundreds of concurrent purchase requests hit the order service within milliseconds, contending for limited stock (e.g., 50 threads competing for the final 5 items).

An order checkout workflow spans multiple distributed boundaries:
1. Validating request payload and idempotency keys.
2. Acquiring distributed concurrency locks.
3. Decrementing inventory in MongoDB.
4. Persisting the initial order document.
5. Emitting an asynchronous domain event (`order-placed`) to Apache Kafka to trigger payment processing and shipping dispatch workers.

The hardest technical challenge solved in this architecture was **guaranteeing zero inventory overselling under burst traffic while preventing phantom stock deductions during dual-commit partial failures**.

---

## 2. What Made It Hard

### 2.1 The Heisenbug of High-Concurrency Race Conditions
Under low-to-moderate traffic or single-threaded integration tests, conventional read-modify-write patterns (`product.getStock() >= qty` followed by `product.setStock(...)` and `repository.save()`) pass reliably. However, under burst traffic, multiple threads interleave between the read and write operations. Two or more threads read `stock = 1`, both evaluate `stock >= 1` as `true`, and both execute decrements, driving inventory into negative numbers (`stock = -4`).

### 2.2 Database Connection Pool Exhaustion vs. Lock Contention
Relying on traditional relational database row-level pessimistic locking (`SELECT ... FOR UPDATE`) under sudden thundering herds holds database connections open across network round trips. Under high contention, connection pools saturate within seconds, triggering thread starvation, cascading HTTP 504 gateway timeouts, and system-wide latency collapse.

### 2.3 The "Dual-Commit" Partial Failure Gap
In a distributed microservice environment, atomic multi-system transactions do not exist across independent datastores (MongoDB + Kafka). If inventory is decremented in MongoDB, but the JVM crashes, the network partitions, or Kafka rejects the produce request before acknowledging `order-placed`:
* Inventory is permanently decremented in the database.
* The client receives an HTTP 500 failure.
* Downstream fulfillment is never triggered, creating irreversible **phantom inventory loss**.

### 2.4 Idempotency Key Mutation / Replay Tampering
When clients experience network timeouts, client-side HTTP retry logic automatically resends requests with the same `Idempotency-Key` header. A naive idempotency filter that checks only key existence would return the cached response even if a buggy client or malicious actor mutated the `productId` or `quantity` in the payload body, corrupting transactional state.

---

## 3. How We Narrowed It Down

### Step 1: Multi-Threaded Concurrency Test
We engineered a multi-threaded test harness using Java's `ExecutorService` and `CountDownLatch` (`OrderServiceConcurrencyTest.java`):
* 50 concurrent worker threads were synchronized and released together against a single product initialized with `stock = 1`.
* Monitored thread execution and database state.
* **Finding:** Without strict distributed serialization and atomic database preconditions, thread interleaving caused multiple threads to evaluate `stock >= 1` simultaneously, driving stock negative and creating duplicate orders.

### Step 2: Fault Injection Across Distributed Boundaries
We injected artificial network latency and runtime exceptions immediately after the MongoDB inventory update but *prior* to Kafka event emission:
* **Finding:** Confirmed the distributed dual-commit gap. MongoDB retained the decremented stock count, but Kafka topics never received the order event, leaving the order orphaned with no compensating rollback.

### Step 3: Simulating JVM Stop-The-World (STW) Pauses
We introduced artificial thread sleeps simulating GC pauses that exceeded the Redis lock lease expiration (3,000ms):
* **Finding:** When a thread paused past its lease time, Redis auto-released the lock. Another waiting thread acquired the lock and decremented stock. When the first thread resumed, it executed its write as well, causing interleaved state corruption. This proved that **distributed locks alone cannot guarantee consistency without database-level atomic guards**.

---

## 4. The Solution: Three-Tiered Distributed Defense-in-Depth

To eliminate single points of failure, we designed and implemented a **three-tiered defense-in-depth architecture**:

```
[ Incoming Client Request ]
             │ (Idempotency-Key Header + JSON Payload)
             ▼
┌─────────────────────────────────────────────────────────────┐
│ Tier 1: Two-Phase Idempotency with SHA-256 Fingerprinting   │
│ • Computes SHA-256(productId + ":" + quantity)              │
│ • Inserts IN_PROGRESS record (backed by MongoDB unique key) │
│ • Duplicate mid-flight? -> HTTP 409 Conflict                │
│ • Payload altered on retry? -> HTTP 422 Unprocessable Entity│
│ • Completed identical key? -> Return cached HTTP 201 DTO    │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│ Tier 2: Redisson Distributed Lock                           │
│ • Key format: lock:inventory:{productId}                    │
│ • Fine-grained per-product partitioning (No global lock)    │
│ • Non-blocking wait: tryLock(500ms, 3000ms TTL)             │
│ • Microsecond in-memory serialization                       │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│ Tier 3: In-Database Atomic Concurrency Guard (MongoDB)      │
│ • Query:  { _id: productId, stock: { $gte: quantity } }     │
│ • Update: { $inc: { stock: -quantity } }                    │
│ • If modifiedCount == 0 -> throw OutOfStockException        │
│ • Failsafe protection against GC pauses or lock evictions   │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│ Saga Compensation & Resilient Rollback                      │
│ • Order doc save fails? -> Immediate $inc: {stock: +qty}    │
│ • Downstream payment fails? -> 'order-payment-failed' event │
│ • CompensationConsumer acquires Redis lock & restores stock │
│ • Guaranteed lock release in finally { lock.unlock() }      │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Key Code Implementations

### 5.1 Atomic Stock Decrement Guard (`OrderService.java`)
```java
// Atomic MongoDB conditional decrement guarantees zero overselling
Query query = Query.query(Criteria.where("_id").is(request.getProductId())
        .and("stock").gte(request.getQuantity()));
Update update = new Update()
        .inc("stock", -request.getQuantity())
        .set("updatedAt", Instant.now());

UpdateResult updateResult = mongoTemplate.updateFirst(query, update, Product.class);
if (updateResult.getModifiedCount() == 0) {
    throw new OutOfStockException(String.format(
        "Product is out of stock. Requested: %d, Available: %d.",
        request.getQuantity(), product.getStock()));
}
```

### 5.2 Cryptographic Payload Fingerprinting (`IdempotencyService.java`)
```java
public String computePayloadHash(String productId, Integer quantity) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((productId + ":" + quantity).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 algorithm not available", e);
    }
}
```

### 5.3 Asynchronous Saga Inventory Rollback (`InventoryCompensationService.java`)
```java
public void compensateInventory(OrderPaymentFailedEvent event) {
    String lockKey = LOCK_PREFIX + event.getProductId();
    RLock lock = redissonClient.getLock(lockKey);
    boolean acquired = false;

    try {
        acquired = lock.tryLock(500, 3000, TimeUnit.MILLISECONDS);
        if (acquired) {
            // Replenish stock atomically in MongoDB
            Query query = Query.query(Criteria.where("_id").is(event.getProductId()));
            Update update = new Update().inc("stock", event.getQuantity());
            mongoTemplate.updateFirst(query, update, Product.class);

            // Record compensation in order status audit history
            Order order = orderRepository.findByOrderId(event.getOrderId()).orElse(null);
            if (order != null) {
                order.addStatusHistory(OrderStatus.FAILED,
                    String.format("Inventory compensation completed: %d unit(s) restored to stock", event.getQuantity()));
                orderRepository.save(order);
            }
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        if (acquired && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

---

## 6. Verification & Quantitative Results

| Metric | Without Multi-Tier Defense | With Multi-Tier Defense-in-Depth |
| :--- | :--- | :--- |
| **50 Concurrent Threads (Stock: 1)** | Multiple orders accepted (negative stock) | **Exactly 1 order accepted, 49 rejected** |
| **Final Stock Integrity** | Corrupted / Negative | **Exactly 0 (Zero overselling)** |
| **Duplicate Request Replay** | Re-decremented inventory | **HTTP 200/201 Cached response returned** |
| **Payload Tampering on Retry** | Corrupted order amounts | **HTTP 422 Unprocessable Entity rejected** |
| **Payment Gateway Failure** | Stock lost permanently | **Automated Saga replenishment to stock** |
| **Checkout Latency** | Blocked on payment/shipping APIs | **Immediate response upon Kafka event emission** |

---

## 7. Architectural Lessons Learned

1. **Distributed Locks Alone Are Insufficient:** Distributed locks can expire prematurely during GC pauses, network jitter, or process stalls. Always back distributed locks with atomic database-level preconditions (`stock >= qty`).
2. **Bind Idempotency Keys to Cryptographic Hashes:** A key identifier alone is vulnerable to retry payload corruption. Hashing request parameters (`SHA-256`) ensures deterministic request deduplication.
3. **Embrace Asynchronous Saga Compensation:** Instead of distributed two-phase commits (2PC) that kill availability, adopt eventual consistency with deterministic saga rollbacks via Kafka event streams.
