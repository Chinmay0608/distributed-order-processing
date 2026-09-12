package com.distributed.orderservice;

import com.distributed.orderservice.dto.request.PlaceOrderRequest;
import com.distributed.orderservice.dto.response.OrderResponse;
import com.distributed.orderservice.event.OrderPlacedEvent;
import com.distributed.orderservice.exception.LockAcquisitionException;
import com.distributed.orderservice.exception.OutOfStockException;
import com.distributed.orderservice.model.Order;
import com.distributed.orderservice.model.Product;
import com.distributed.orderservice.repository.OrderRepository;
import com.distributed.orderservice.repository.ProductRepository;
import com.distributed.orderservice.service.IdempotencyService;
import com.distributed.orderservice.service.InventoryLockService;
import com.distributed.orderservice.service.KafkaEventPublisher;
import com.distributed.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OrderServiceConcurrencyTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private InventoryLockService inventoryLockService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    private OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simulates database stock atomically in memory
    private final AtomicInteger currentStock = new AtomicInteger(1);
    private final AtomicInteger totalOrdersCreated = new AtomicInteger(0);

    // Simulates Redis distributed lock behavior across threads
    private final ReentrantLock distributedLockSimulator = new ReentrantLock();

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                productRepository,
                orderRepository,
                mongoTemplate,
                inventoryLockService,
                idempotencyService,
                kafkaEventPublisher,
                objectMapper
        );

        currentStock.set(1);
        totalOrdersCreated.set(0);

        // Mock idempotency to always allow distinct keys
        when(idempotencyService.computePayloadHash(anyString(), any())).thenReturn("mockHash");
        when(idempotencyService.checkOrStart(anyString(), anyString())).thenReturn(Optional.empty());

        // Mock Redisson RLock backed by our lock simulator
        RLock mockLock = mock(RLock.class);
        when(inventoryLockService.acquireLock(anyString())).thenAnswer(invocation -> {
            boolean acquired = distributedLockSimulator.tryLock(500, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new LockAcquisitionException("High contention on product inventory. Please retry.");
            }
            return mockLock;
        });

        doAnswer(invocation -> {
            if (distributedLockSimulator.isHeldByCurrentThread()) {
                distributedLockSimulator.unlock();
            }
            return null;
        }).when(inventoryLockService).releaseLock(any());

        // Mock product lookup with current stock
        when(productRepository.findById("prod-race-1")).thenAnswer(invocation ->
                Optional.of(new Product("prod-race-1", "Limited Edition Item", "Desc", 499.0, currentStock.get()))
        );

        // Mock atomic MongoDB updateFirst: only succeeds if stock >= 1
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class))).thenAnswer(invocation -> {
            int prev = currentStock.getAndUpdate(s -> s >= 1 ? s - 1 : s);
            UpdateResult res = mock(UpdateResult.class);
            when(res.getModifiedCount()).thenReturn(prev >= 1 ? 1L : 0L);
            return res;
        });

        // Mock orderRepository.save
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            totalOrdersCreated.incrementAndGet();
            return invocation.getArgument(0);
        });
    }

    @Test
    void testConcurrentOrderPlacement_RaceConditionGuard() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ConcurrentLinkedQueue<OrderResponse> successfulResponses = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> failedResponses = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all 50 threads to fire simultaneously
                    String uniqueKey = "idemp-" + UUID.randomUUID();
                    PlaceOrderRequest request = new PlaceOrderRequest("prod-race-1", 1);
                    OrderResponse response = orderService.placeOrder(request, uniqueKey);
                    successfulResponses.add(response);
                } catch (Throwable t) {
                    failedResponses.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all 50 threads
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assertions
        assertEquals(true, completed, "All 50 threads should complete within timeout");
        assertEquals(1, successfulResponses.size(), "Exactly 1 concurrent buyer must succeed");
        assertEquals(49, failedResponses.size(), "Exactly 49 concurrent buyers must fail");

        // Verify failures are OutOfStockException or LockAcquisitionException (409)
        int outOfStockCount = 0;
        int lockAcquisitionCount = 0;
        for (Throwable failure : failedResponses) {
            if (failure instanceof OutOfStockException) {
                outOfStockCount++;
            } else if (failure instanceof LockAcquisitionException) {
                lockAcquisitionCount++;
            } else {
                fail("Unexpected failure type: " + failure);
            }
        }

        System.out.printf(">>> Concurrency Test Breakdown: Succeeded=%d, OutOfStock=%d, LockTimeout=%d, TotalFailures=%d%n",
                successfulResponses.size(), outOfStockCount, lockAcquisitionCount, failedResponses.size());

        // Both gates work in tandem: total failures must equal 49
        assertEquals(49, outOfStockCount + lockAcquisitionCount, "All 49 failures must be either OutOfStock or LockAcquisition failure");

        // Assert final stock is exactly 0 (no negative stock, zero overselling)
        assertEquals(0, currentStock.get(), "Final stock must be exactly 0");

        // Assert total orders saved is exactly 1
        assertEquals(1, totalOrdersCreated.get(), "Total orders saved in MongoDB must be exactly 1");
    }
}
