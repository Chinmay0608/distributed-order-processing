# Hour-by-Hour Implementation Build Order (8-Hour Day)

This schedule outlines how the Distributed Order Processing project is built across an 8-hour sprint, dedicating ~1.5–2 hours to the minimal React Vite frontend while prioritizing backend correctness, distributed concurrency tests, and Kafka streaming pipelines.

---

## 8-Hour Schedule

```
  00:00 - 01:30   [ Module 1: DB & Infrastructure Setup ]
  01:30 - 03:00   [ Module 1: Redis Lock, Idempotency, REST API & Concurrency Test ]
  03:00 - 04:30   [ Module 2: Kafka Topics, Payment Worker & Failure Triggers ]
  04:30 - 05:30   [ Module 2: Shipping Worker, Compensation Saga & Unit Tests ]
  05:30 - 07:15   [ Module 3: Minimal React Vite Frontend (~1.75 hrs) ]
  07:15 - 08:00   [ Integration Verification, Docker Compose & Demo Run ]
```

---

### Phase 1: Infrastructure & Data Foundation (00:00 – 01:30)
- **Goal:** Establish multi-container runtime and MongoDB data persistence models.
- **Tasks:**
  - Define [`docker-compose.yml`](file:///d:/Projects/Distributed%20order%20processing/docker-compose.yml) with `mongo:7.0`, `redis:7.2-alpine`, `confluentinc/cp-zookeeper:7.5.0`, `confluentinc/cp-kafka:7.5.0`.
  - Initialize Spring Boot 3.2.4 scaffolding in `order-service/`.
  - Create MongoDB documents: [`Product.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/model/Product.java), [`Order.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/model/Order.java), [`IdempotencyRecord.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/model/IdempotencyRecord.java).
  - Seed catalog items via [`MongoConfig.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/config/MongoConfig.java) (including low-stock item with stock=1 and payment failure trigger item).
- **Verification:** Run `mvnw compile` in `order-service/`.

---

### Phase 2: Core Concurrency, Idempotency & REST Intake (01:30 – 03:00)
- **Goal:** Implement and test the 10-step order placement flow with Redis distributed locking and idempotency.
- **Tasks:**
  - Configure Redisson client in [`RedissonConfig.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/config/RedissonConfig.java).
  - Implement [`InventoryLockService.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/service/InventoryLockService.java) guarding `lock:inventory:{productId}` with 500ms wait / 3000ms TTL.
  - Implement [`IdempotencyService.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/service/IdempotencyService.java) with SHA-256 payload hashing, caching, and 422 mismatch rejection.
  - Implement [`OrderService.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/service/OrderService.java) and REST controllers [`ProductController.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/controller/ProductController.java) and [`OrderController.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/controller/OrderController.java).
  - Add [`GlobalExceptionHandler.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/main/java/com/distributed/orderservice/exception/GlobalExceptionHandler.java) mapping domain exceptions to HTTP 400, 404, 409, 422, 500.
- **Verification:**
  - Run [`OrderServiceConcurrencyTest.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/test/java/com/distributed/orderservice/OrderServiceConcurrencyTest.java): 50 simultaneous threads targeting 1 stock item -> confirms exactly 1 success (`201 Created`), 49 failures (`409 Conflict`), final stock = 0, total orders = 1.
  - Run [`IdempotencyServiceUnitTest.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/test/java/com/distributed/orderservice/IdempotencyServiceUnitTest.java) and [`OrderServiceUnitTest.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/test/java/com/distributed/orderservice/OrderServiceUnitTest.java) (10/10 tests pass).

---

### Phase 3: Kafka Streaming Pipeline & Payment Worker (03:00 – 04:30)
- **Goal:** Set up asynchronous event consumers in `processing-service/`.
- **Tasks:**
  - Configure Kafka consumer container factory and producers in [`KafkaConfig.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/main/java/com/distributed/processingservice/config/KafkaConfig.java).
  - Implement [`PaymentConsumer.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/main/java/com/distributed/processingservice/consumer/PaymentConsumer.java) and [`PaymentProcessor.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/main/java/com/distributed/processingservice/service/PaymentProcessor.java).
  - Wire deterministic failure triggers: `productId.equals("prod-fail-payment")` or `quantity == 99`.
  - Happy path emits `order-payment-processed`; failure path emits `order-payment-failed`.
- **Verification:** Run [`PaymentProcessorUnitTest.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/test/java/com/distributed/processingservice/PaymentProcessorUnitTest.java) (3/3 pass).

---

### Phase 4: Shipping Worker & Inventory Compensation Saga (04:30 – 05:30)
- **Goal:** Complete warehouse fulfillment and transactional compensation for payment declines.
- **Tasks:**
  - Implement [`ShippingConsumer.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/main/java/com/distributed/processingservice/consumer/ShippingConsumer.java) and [`ShippingProcessor.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/main/java/com/distributed/processingservice/service/ShippingProcessor.java) to generate tracking numbers (`TRK-...`), transition orders to `SHIPPED`, and emit `order-shipped`.
  - Implement [`CompensationConsumer.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/main/java/com/distributed/processingservice/consumer/CompensationConsumer.java) and [`InventoryCompensationService.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/main/java/com/distributed/processingservice/service/InventoryCompensationService.java): acquires Redis lock `lock:inventory:{productId}`, restores inventory via MongoDB `$inc: { stock: +qty }`, and logs compensation.
- **Verification:** Run [`ShippingProcessorUnitTest.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/test/java/com/distributed/processingservice/ShippingProcessorUnitTest.java) and [`CompensationConsumerUnitTest.java`](file:///d:/Projects/Distributed%20order%20processing/processing-service/src/test/java/com/distributed/processingservice/CompensationConsumerUnitTest.java) (5/5 pass).

---

### Phase 5: Minimal React Frontend Module 3 (05:30 – 07:15, ~1.75 hrs)
- **Goal:** Deliver the 3 required visual screens with zero mock overhead, targeting the real backend at `http://localhost:8080`.
- **Tasks:**
  - Initialize Vite React project in `frontend/` with minimal styling in [`index.css`](file:///d:/Projects/Distributed%20order%20processing/frontend/src/index.css).
  - Implement [`api.js`](file:///d:/Projects/Distributed%20order%20processing/frontend/src/api.js): pure fetch calls to `http://localhost:8080/api` with `simulateConcurrentBuyers` runner.
  - Implement Screen 1: [`ProductList.jsx`](file:///d:/Projects/Distributed%20order%20processing/frontend/src/components/ProductList.jsx) with live stock badges and demo scenario tags.
  - Implement Screen 2: [`PlaceOrder.jsx`](file:///d:/Projects/Distributed%20order%20processing/frontend/src/components/PlaceOrder.jsx) with order form, idempotency key manager, and **Simulate Concurrent Buyers** 10-request race condition blast button with live scoreboard.
  - Implement Screen 3: [`OrderStatus.jsx`](file:///d:/Projects/Distributed%20order%20processing/frontend/src/components/OrderStatus.jsx) with 1.5s live polling, visual Kafka pipeline stepper (`PLACED` -> `PAYMENT_PROCESSED` -> `SHIPPED`), pulsing stage indicator, and chronological `statusHistory` audit log.
  - Wire state and tabs in [`App.jsx`](file:///d:/Projects/Distributed%20order%20processing/frontend/src/App.jsx).
- **Verification:** Run `npm run build` in `frontend/` -> builds in < 2 seconds with zero errors.

---

### Phase 6: Full-Stack Integration & Demonstration (07:15 – 08:00)
- **Goal:** Verify end-to-end flow across Docker containers and capture live race-condition demo.
- **Tasks:**
  - Build and start full stack via `docker compose up --build`.
  - Open `http://localhost:3000` in browser.
  - Execute 10-buyer concurrent blast on the 1-stock item -> confirm 1 success, 9 out-of-stock.
  - Verify live polling stepper on Order Status screen transitions across the async Kafka pipeline.
