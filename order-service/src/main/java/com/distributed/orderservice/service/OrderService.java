package com.distributed.orderservice.service;

import com.distributed.orderservice.dto.request.PlaceOrderRequest;
import com.distributed.orderservice.dto.response.OrderResponse;
import com.distributed.orderservice.dto.response.OrderStatusResponse;
import com.distributed.orderservice.event.OrderPlacedEvent;
import com.distributed.orderservice.exception.OutOfStockException;
import com.distributed.orderservice.exception.ResourceNotFoundException;
import com.distributed.orderservice.model.IdempotencyRecord;
import com.distributed.orderservice.model.Order;
import com.distributed.orderservice.model.OrderStatus;
import com.distributed.orderservice.model.Product;
import com.distributed.orderservice.repository.OrderRepository;
import com.distributed.orderservice.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;
    private final InventoryLockService inventoryLockService;
    private final IdempotencyService idempotencyService;
    private final KafkaEventPublisher kafkaEventPublisher;
    private final ObjectMapper objectMapper;

    public OrderService(ProductRepository productRepository,
                        OrderRepository orderRepository,
                        MongoTemplate mongoTemplate,
                        InventoryLockService inventoryLockService,
                        IdempotencyService idempotencyService,
                        KafkaEventPublisher kafkaEventPublisher,
                        ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.mongoTemplate = mongoTemplate;
        this.inventoryLockService = inventoryLockService;
        this.idempotencyService = idempotencyService;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the complete 10-step Place Order sequence with Redis distributed locking,
     * idempotency protection, atomic inventory deduction, and Kafka publishing.
     */
    public OrderResponse placeOrder(PlaceOrderRequest request, String idempotencyKey) {
        // Step 1: Validate input
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required.");
        }
        if (request.getProductId() == null || request.getProductId().isBlank()) {
            throw new IllegalArgumentException("productId is required.");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero.");
        }

        // Step 2: Check Idempotency & SHA-256 payload hash
        String payloadHash = idempotencyService.computePayloadHash(request.getProductId(), request.getQuantity());
        Optional<IdempotencyRecord> cached = idempotencyService.checkOrStart(idempotencyKey, payloadHash);
        if (cached.isPresent()) {
            log.info("Returning cached order response for key '{}'", idempotencyKey);
            return objectMapper.convertValue(cached.get().getResponseBody(), OrderResponse.class);
        }

        // Step 3: Acquire Redis distributed lock
        RLock lock = null;
        try {
            lock = inventoryLockService.acquireLock(request.getProductId());
        } catch (Exception ex) {
            idempotencyService.markFailed(idempotencyKey);
            throw ex;
        }

        try {
            // Step 4: Check product existence and available stock
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product with id '" + request.getProductId() + "' not found."));

            if (product.getStock() < request.getQuantity()) {
                throw new OutOfStockException(String.format(
                        "Product is out of stock. Requested: %d, Available: %d.",
                        request.getQuantity(), product.getStock()));
            }

            // Step 5: Atomic stock decrement in MongoDB
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

            // Step 6: Create and persist Order in MongoDB
            String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            double totalAmount = Math.round(product.getPrice() * request.getQuantity() * 100.0) / 100.0;
            Order order = new Order(
                    orderId,
                    idempotencyKey,
                    request.getProductId(),
                    product.getName(),
                    request.getQuantity(),
                    product.getPrice(),
                    totalAmount,
                    OrderStatus.PLACED
            );

            Order savedOrder;
            try {
                savedOrder = orderRepository.save(order);
            } catch (Exception e) {
                // Compensate stock if order document creation fails
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(request.getProductId())),
                        new Update().inc("stock", request.getQuantity()),
                        Product.class
                );
                throw e;
            }

            OrderResponse response = new OrderResponse(
                    savedOrder.getOrderId(),
                    savedOrder.getIdempotencyKey(),
                    savedOrder.getProductId(),
                    savedOrder.getProductName(),
                    savedOrder.getQuantity(),
                    savedOrder.getTotalAmount(),
                    savedOrder.getStatus().name(),
                    savedOrder.getCreatedAt()
            );

            // Step 8: Mark Idempotency Record as COMPLETED with cached response body
            idempotencyService.markCompleted(idempotencyKey, orderId, 201, response);

            // Step 9: Publish Kafka Event
            OrderPlacedEvent event = new OrderPlacedEvent(
                    UUID.randomUUID().toString(),
                    savedOrder.getOrderId(),
                    savedOrder.getProductId(),
                    savedOrder.getQuantity(),
                    savedOrder.getTotalAmount()
            );
            kafkaEventPublisher.publishOrderPlaced(event);

            // Step 10: Return HTTP 201 Created response DTO
            return response;

        } catch (Exception ex) {
            idempotencyService.markFailed(idempotencyKey);
            throw ex;
        } finally {
            // Step 7: Guaranteed lock release in finally block
            inventoryLockService.releaseLock(lock);
        }
    }

    public OrderStatusResponse getOrderStatus(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order '" + orderId + "' not found."));

        return new OrderStatusResponse(
                order.getOrderId(),
                order.getProductId(),
                order.getProductName(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getStatusHistory()
        );
    }
}
