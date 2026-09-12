# Graph Report - Distributed order processing  (2026-09-12)

## Corpus Check
- 85 files · ~27,641 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 742 nodes · 1333 edges · 42 communities (27 shown, 14 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 67 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `deaed70a`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- OrderPaymentProcessedEvent
- OrderPaymentFailedEvent
- Product
- Order
- OrderServiceUnitTest.java
- org.springframework.context.annotation.Bean
- OrderProcessingEndToEndIntegrationTest.java
- package.json
- OrderStatusResponse
- Order
- ErrorResponse
- ProductResponse
- IdempotencyRecord
- High-Level Design (HLD): Distributed Order Processing System
- Distributed Order Processing System
- order-service/mvnw
- processing-service/mvnw
- org.springframework.boot.autoconfigure.SpringBootApplication
- com.distributed:order-service
- com.distributed:processing-service
- OrderPlacedEvent
- App.jsx
- Product
- Complete Project File Index
- OrderShippedEvent
- Understanding Your Distributed Order Processing Project — From Ground Zero
- 7-Day Deep Understanding Plan — Distributed Order Processing System
- org.springframework.data.mongodb.repository.MongoRepository
- InventoryCompensationService
- OrderRepository
- 8-Hour Schedule
- Core Design Principles
- Project Guidelines & Integrated Skills
- org.springframework.data.mongodb.core.mapping.Document
- PaymentProcessorUnitTest
- Ponytail Skill
- org.slf4j.Logger
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
- `IdempotencyService` --references--> `IdempotencyKeyRepository`  [EXTRACTED]
  order-service/src/main/java/com/distributed/orderservice/service/IdempotencyService.java → order-service/src/main/java/com/distributed/orderservice/repository/IdempotencyKeyRepository.java

## Import Cycles
- None detected.

## Communities (42 total, 14 thin omitted)

### Community 3 - "Order"
Cohesion: 0.05
Nodes (9): StatusHistoryEntry, Order, OrderStatus, CANCELLED, FAILED, PAYMENT_PROCESSED, PLACED, SHIPPED (+1 more)

### Community 4 - "OrderServiceUnitTest.java"
Cohesion: 0.06
Nodes (27): com.fasterxml.jackson.databind.ObjectMapper, java.util.concurrent.locks.ReentrantLock, PostMapping, RequestMapping, RestController, OrderController, PlaceOrderRequest, OrderResponse (+19 more)

### Community 5 - "org.springframework.context.annotation.Bean"
Cohesion: 0.09
Nodes (22): ConcurrentKafkaListenerContainerFactory, ConsumerFactory, KafkaTemplate, KafkaProducerConfig, MongoConfig, RedissonConfig, WebCorsConfig, org.apache.kafka.clients.admin.NewTopic (+14 more)

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

### Community 12 - "IdempotencyRecord"
Cohesion: 0.11
Nodes (5): IdempotencyRecord, IdempotencyStatus, COMPLETED, FAILED, IN_PROGRESS

### Community 13 - "High-Level Design (HLD): Distributed Order Processing System"
Cohesion: 0.04
Nodes (47): 10. Deep-Dive Flow 4: Compensation Saga, 11. Failure Mode Analysis, 12. Scaling Discussion, 13. Architectural Trade-Offs Matrix, 14. 60-Second Interview Pitch, 1. In-JVM Mock Test (`OrderServiceConcurrencyTest.java`), 1. Order Service (`com.distributed.orderservice`), 1. Problem Statement (+39 more)

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

### Community 24 - "Complete Project File Index"
Cohesion: 0.12
Nodes (15): 1. `order-service` Unit & Concurrency Tests: 10/10 Passed, 1. Testcontainers End-to-End Test: [`OrderProcessingEndToEndIntegrationTest.java`](file:///d:/Projects/Distributed%20order%20processing/order-service/src/test/java/com/distributed/orderservice/OrderProcessingEndToEndIntegrationTest.java), 2. Frontend: Concurrent Race Simulation in [`PlaceOrder.jsx`](file:///d:/Projects/Distributed%20order%20processing/frontend/src/components/PlaceOrder.jsx), 2. `processing-service` Unit Tests: 5/5 Passed, 3. Frontend Production Build: Built Cleanly, Complete Project File Index, Container Startup Code:, Detailed Code Deep Dives (+7 more)

### Community 26 - "Understanding Your Distributed Order Processing Project — From Ground Zero"
Cohesion: 0.14
Nodes (13): 3.1 The Race Condition (the core problem), 3.2 Idempotency (the "don't double-charge me" problem), 3.3 Kafka / Event-Driven Pipeline (the "don't make the customer wait" problem), 3.4 The Compensation Saga (the "what if it fails halfway" problem), 3.5 Why MongoDB and not just plain SQL?, Part 1: What Is This Project, In Plain English?, Part 2: Why This Topic? (What This Project Proves About You), Part 3: The Five Concepts You Must Deeply Understand (+5 more)

### Community 27 - "7-Day Deep Understanding Plan — Distributed Order Processing System"
Cohesion: 0.20
Nodes (9): 7-Day Deep Understanding Plan — Distributed Order Processing System, A Note on Pacing, Day 1 — Trace the Request, End to End, Day 2 — The Lock, In Isolation, Day 3 — Break It On Purpose (The Most Important Day), Day 4 — Watch the Async Pipeline With Your Own Eyes, Day 5 — The Compensation Saga, Live, Day 6 — Extend It Yourself (No AI Help) (+1 more)

### Community 28 - "org.springframework.data.mongodb.repository.MongoRepository"
Cohesion: 0.38
Nodes (4): IdempotencyKeyRepository, org.springframework.data.mongodb.repository.MongoRepository, org.springframework.stereotype.Repository, ProductRepository

### Community 29 - "InventoryCompensationService"
Cohesion: 0.33
Nodes (6): org.springframework.kafka.annotation.KafkaListener, org.springframework.stereotype.Component, CompensationConsumer, PaymentConsumer, InventoryCompensationService, PaymentProcessor

### Community 30 - "OrderRepository"
Cohesion: 0.31
Nodes (4): ShippingConsumer, OrderRepository, ShippingProcessor, ShippingProcessorUnitTest

### Community 31 - "8-Hour Schedule"
Cohesion: 0.22
Nodes (8): 8-Hour Schedule, Hour-by-Hour Implementation Build Order (8-Hour Day), Phase 1: Infrastructure & Data Foundation (00:00 – 01:30), Phase 2: Core Concurrency, Idempotency & REST Intake (01:30 – 03:00), Phase 3: Kafka Streaming Pipeline & Payment Worker (03:00 – 04:30), Phase 4: Shipping Worker & Inventory Compensation Saga (04:30 – 05:30), Phase 5: Minimal React Frontend Module 3 (05:30 – 07:15, ~1.75 hrs), Phase 6: Full-Stack Integration & Demonstration (07:15 – 08:00)

### Community 32 - "Core Design Principles"
Cohesion: 0.29
Nodes (6): 1. Visual Hierarchy & Spatial Harmony, 2. Modern Typography & Legibility, 3. State & Feedback Ergonomics, 4. Zero-Bloat Execution, Core Design Principles, UI/UX Pro Max

### Community 33 - "Project Guidelines & Integrated Skills"
Cohesion: 0.40
Nodes (4): 1. Graphify (`graphify`), 2. Ponytail (`ponytail`), 3. UI/UX Pro Max (`ui-ux-pro-max`), Project Guidelines & Integrated Skills

### Community 36 - "Ponytail Skill"
Cohesion: 0.50
Nodes (3): Core Rules, Ponytail Skill, The Ladder of Simplicity

### Community 37 - "org.slf4j.Logger"
Cohesion: 0.33
Nodes (4): org.slf4j.Logger, org.springframework.kafka.core.KafkaTemplate, org.springframework.stereotype.Service, ProcessingEventPublisher

## Knowledge Gaps
- **124 isolated node(s):** `name`, `private`, `version`, `type`, `dev` (+119 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 391 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **14 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Order` connect `Order` to `org.springframework.data.mongodb.core.mapping.Document`, `OrderServiceUnitTest.java`, `org.slf4j.Logger`, `PaymentProcessorUnitTest.java`, `org.springframework.data.mongodb.repository.MongoRepository`, `OrderRepository`?**
  _High betweenness centrality (0.073) - this node is a cross-community bridge._
- **Why does `Order` connect `Order` to `org.springframework.data.mongodb.core.mapping.Document`, `OrderServiceUnitTest.java`, `OrderProcessingEndToEndIntegrationTest.java`, `OrderStatusResponse`, `org.springframework.data.mongodb.repository.MongoRepository`?**
  _High betweenness centrality (0.068) - this node is a cross-community bridge._
- **Why does `Product` connect `Product` to `org.springframework.data.mongodb.core.mapping.Document`, `OrderServiceUnitTest.java`, `OrderProcessingEndToEndIntegrationTest.java`, `ProductResponse`, `org.springframework.data.mongodb.repository.MongoRepository`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **What connects `name`, `private`, `version` to the rest of the system?**
  _124 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `OrderPaymentProcessedEvent` be split into smaller, more focused modules?**
  _Cohesion score 0.13071895424836602 - nodes in this community are weakly interconnected._
- **Should `OrderPaymentFailedEvent` be split into smaller, more focused modules?**
  _Cohesion score 0.11688311688311688 - nodes in this community are weakly interconnected._
- **Should `Product` be split into smaller, more focused modules?**
  _Cohesion score 0.1111111111111111 - nodes in this community are weakly interconnected._