# Graph Report - Distributed order processing  (2026-09-15)

## Corpus Check
- 81 files · ~26,559 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 654 nodes · 1249 edges · 29 communities (21 shown, 8 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 67 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `024f3ddf`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- OrderPaymentProcessedEvent
- OrderPlacedEvent
- OrderServiceUnitTest.java
- Order
- OrderPaymentFailedEvent
- org.springframework.context.annotation.Bean
- OrderProcessingEndToEndIntegrationTest.java
- package.json
- OrderStatusResponse
- Order
- ErrorResponse
- ProductResponse
- OrderResponse
- Distributed Order Processing System
- order-service/mvnw
- processing-service/mvnw
- org.springframework.boot.autoconfigure.SpringBootApplication
- com.distributed:order-service
- com.distributed:processing-service
- App.jsx
- Product
- 8-Hour Schedule
- Core Design Principles
- Project Guidelines & Integrated Skills
- Ponytail Skill
- rules/graphify.md
- ponytail.md
- ui-ux-pro-max.md
- workflows/graphify.md

## God Nodes (most connected - your core abstractions)
1. `Order` - 39 edges
2. `Order` - 38 edges
3. `Product` - 28 edges
4. `OrderPaymentFailedEvent` - 27 edges
5. `OrderStatusResponse` - 26 edges
6. `OrderResponse` - 25 edges
7. `IdempotencyRecord` - 25 edges
8. `OrderPaymentProcessedEvent` - 25 edges
9. `OrderPlacedEvent` - 23 edges
10. `Product` - 23 edges

## Surprising Connections (you probably didn't know these)
- `IdempotencyKeyRepository` --references--> `IdempotencyRecord`  [EXTRACTED]
  order-service/src/main/java/com/distributed/orderservice/repository/IdempotencyKeyRepository.java → order-service/src/main/java/com/distributed/orderservice/model/IdempotencyRecord.java
- `Order` --references--> `StatusHistoryEntry`  [EXTRACTED]
  order-service/src/main/java/com/distributed/orderservice/model/Order.java → order-service/src/main/java/com/distributed/orderservice/model/StatusHistoryEntry.java
- `OrderRepository` --references--> `Order`  [EXTRACTED]
  order-service/src/main/java/com/distributed/orderservice/repository/OrderRepository.java → order-service/src/main/java/com/distributed/orderservice/model/Order.java
- `ProductRepository` --references--> `Product`  [EXTRACTED]
  order-service/src/main/java/com/distributed/orderservice/repository/ProductRepository.java → order-service/src/main/java/com/distributed/orderservice/model/Product.java
- `IdempotencyServiceUnitTest` --references--> `IdempotencyKeyRepository`  [EXTRACTED]
  order-service/src/test/java/com/distributed/orderservice/IdempotencyServiceUnitTest.java → order-service/src/main/java/com/distributed/orderservice/repository/IdempotencyKeyRepository.java

## Import Cycles
- None detected.

## Communities (29 total, 8 thin omitted)

### Community 2 - "OrderServiceUnitTest.java"
Cohesion: 0.11
Nodes (21): com.fasterxml.jackson.databind.ObjectMapper, java.util.concurrent.locks.ReentrantLock, RequestMapping, RestController, OrderController, PlaceOrderRequest, OutOfStockException, IdempotencyKeyRepository (+13 more)

### Community 3 - "Order"
Cohesion: 0.05
Nodes (9): StatusHistoryEntry, Order, OrderStatus, CANCELLED, FAILED, PAYMENT_PROCESSED, PLACED, SHIPPED (+1 more)

### Community 4 - "OrderPaymentFailedEvent"
Cohesion: 0.07
Nodes (22): org.junit.jupiter.api.BeforeEach, org.junit.jupiter.api.extension.ExtendWith, org.mockito.junit.jupiter.MockitoExtension, org.redisson.api.RedissonClient, org.redisson.api.RLock, org.slf4j.Logger, org.springframework.kafka.annotation.KafkaListener, org.springframework.kafka.core.KafkaTemplate (+14 more)

### Community 5 - "org.springframework.context.annotation.Bean"
Cohesion: 0.09
Nodes (21): ConcurrentKafkaListenerContainerFactory, ConsumerFactory, KafkaTemplate, KafkaProducerConfig, MongoConfig, RedissonConfig, WebCorsConfig, org.apache.kafka.clients.admin.NewTopic (+13 more)

### Community 6 - "OrderProcessingEndToEndIntegrationTest.java"
Cohesion: 0.08
Nodes (14): KafkaMessageListenerContainer, OrderPlacedEvent, OrderProcessingEndToEndIntegrationTest, org.apache.kafka.clients.consumer.ConsumerRecord, org.junit.jupiter.api.DisplayName, org.springframework.boot.test.context.SpringBootTest, org.springframework.boot.test.web.client.TestRestTemplate, org.springframework.kafka.listener.KafkaMessageListenerContainer (+6 more)

### Community 7 - "package.json"
Cohesion: 0.11
Nodes (17): dependencies, react, react-dom, devDependencies, vite, @vitejs/plugin-react, name, private (+9 more)

### Community 8 - "OrderStatusResponse"
Cohesion: 0.07
Nodes (3): GetMapping, OrderStatusResponse, StatusHistoryEntry

### Community 9 - "Order"
Cohesion: 0.07
Nodes (8): StatusHistoryEntry, Order, OrderStatus, CANCELLED, FAILED, PAYMENT_PROCESSED, PLACED, SHIPPED

### Community 10 - "ErrorResponse"
Cohesion: 0.13
Nodes (9): ErrorResponse, GlobalExceptionHandler, IdempotencyPayloadMismatchException, OrderAlreadyProcessingException, org.springframework.http.ResponseEntity, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException (+1 more)

### Community 11 - "ProductResponse"
Cohesion: 0.08
Nodes (9): ProductController, ProductResponse, ResourceNotFoundException, Product, ProductService, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping (+1 more)

### Community 12 - "OrderResponse"
Cohesion: 0.05
Nodes (10): PostMapping, OrderResponse, LockAcquisitionException, IdempotencyRecord, IdempotencyStatus, COMPLETED, FAILED, IN_PROGRESS (+2 more)

### Community 15 - "Distributed Order Processing System"
Cohesion: 0.10
Nodes (20): 1. `order-service` Tests (10 tests, 0 failures), 1. Start Infrastructure (MongoDB, Redis, Kafka), 1. Why Redis Distributed Lock over Database Pessimistic Locking (`SELECT ... FOR UPDATE`), 2. `processing-service` Tests (5 tests, 0 failures), 2. Run `order-service`, 2. Why Apache Kafka over Direct Synchronous REST Calls, 3. Idempotency & Replay Protection, 3. Run `processing-service` (+12 more)

### Community 16 - "order-service/mvnw"
Cohesion: 0.38
Nodes (8): mvnw script, clean(), die(), exec_maven(), hash_string(), set_java_home(), trim(), verbose()

### Community 17 - "processing-service/mvnw"
Cohesion: 0.38
Nodes (8): mvnw script, clean(), die(), exec_maven(), hash_string(), set_java_home(), trim(), verbose()

### Community 18 - "org.springframework.boot.autoconfigure.SpringBootApplication"
Cohesion: 0.38
Nodes (3): OrderServiceApplication, org.springframework.boot.autoconfigure.SpringBootApplication, ProcessingServiceApplication

### Community 22 - "App.jsx"
Cohesion: 0.32
Nodes (11): generateUUID(), getOrderStatus(), getProducts(), placeOrder(), resetProducts(), simulateConcurrentBuyers(), App(), OrderStatus() (+3 more)

### Community 23 - "Product"
Cohesion: 0.05
Nodes (3): Product, org.springframework.data.mongodb.core.mapping.Document, Product

### Community 31 - "8-Hour Schedule"
Cohesion: 0.22
Nodes (8): 8-Hour Schedule, Hour-by-Hour Implementation Build Order (8-Hour Day), Phase 1: Infrastructure & Data Foundation (00:00 – 01:30), Phase 2: Core Concurrency, Idempotency & REST Intake (01:30 – 03:00), Phase 3: Kafka Streaming Pipeline & Payment Worker (03:00 – 04:30), Phase 4: Shipping Worker & Inventory Compensation Saga (04:30 – 05:30), Phase 5: Minimal React Frontend Module 3 (05:30 – 07:15, ~1.75 hrs), Phase 6: Full-Stack Integration & System Verification (07:15 – 08:00)

### Community 32 - "Core Design Principles"
Cohesion: 0.29
Nodes (6): 1. Visual Hierarchy & Spatial Harmony, 2. Modern Typography & Legibility, 3. State & Feedback Ergonomics, 4. Zero-Bloat Execution, Core Design Principles, UI/UX Pro Max

### Community 33 - "Project Guidelines & Integrated Skills"
Cohesion: 0.40
Nodes (4): 1. Graphify (`graphify`), 2. Ponytail (`ponytail`), 3. UI/UX Pro Max (`ui-ux-pro-max`), Project Guidelines & Integrated Skills

### Community 36 - "Ponytail Skill"
Cohesion: 0.50
Nodes (3): Core Rules, Ponytail Skill, The Ladder of Simplicity

## Knowledge Gaps
- **61 isolated node(s):** `name`, `private`, `version`, `type`, `dev` (+56 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 324 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Order` connect `Order` to `OrderServiceUnitTest.java`, `OrderPaymentFailedEvent`, `Product`?**
  _High betweenness centrality (0.093) - this node is a cross-community bridge._
- **Why does `Order` connect `Order` to `OrderStatusResponse`, `OrderServiceUnitTest.java`, `OrderProcessingEndToEndIntegrationTest.java`, `Product`?**
  _High betweenness centrality (0.088) - this node is a cross-community bridge._
- **Why does `Product` connect `Product` to `OrderServiceUnitTest.java`, `ProductResponse`, `OrderProcessingEndToEndIntegrationTest.java`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **What connects `name`, `private`, `version` to the rest of the system?**
  _61 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `OrderPaymentProcessedEvent` be split into smaller, more focused modules?**
  _Cohesion score 0.06951871657754011 - nodes in this community are weakly interconnected._
- **Should `OrderPlacedEvent` be split into smaller, more focused modules?**
  _Cohesion score 0.11764705882352941 - nodes in this community are weakly interconnected._
- **Should `OrderServiceUnitTest.java` be split into smaller, more focused modules?**
  _Cohesion score 0.1111111111111111 - nodes in this community are weakly interconnected._