package com.distributed.processingservice;

import com.distributed.processingservice.event.OrderPaymentFailedEvent;
import com.distributed.processingservice.model.Order;
import com.distributed.processingservice.model.OrderStatus;
import com.distributed.processingservice.model.Product;
import com.distributed.processingservice.repository.OrderRepository;
import com.distributed.processingservice.service.InventoryCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CompensationConsumerUnitTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private InventoryCompensationService compensationService;

    @BeforeEach
    void setUp() throws InterruptedException {
        compensationService = new InventoryCompensationService(mongoTemplate, orderRepository, redissonClient);
        when(redissonClient.getLock("lock:inventory:prod-fail-payment")).thenReturn(lock);
        when(lock.tryLock(500, 3000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void testCompensation_ReplenishesStockUnderRedisLock() {
        String orderId = "ORD-FAIL-COMP-1";
        String productId = "prod-fail-payment";
        int quantityToRestore = 2;

        Order order = new Order(orderId, "key-fail-1", productId, "Fail Item", quantityToRestore, 99.99, 199.98, OrderStatus.FAILED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderPaymentFailedEvent failedEvent = new OrderPaymentFailedEvent(
                "evt-fail-1", orderId, productId, quantityToRestore, 199.98, "Payment declined"
        );

        compensationService.compensateInventory(failedEvent);

        // Verify stock replenishment in MongoDB
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), updateCaptor.capture(), eq(Product.class));

        // Verify order history updated with compensation record
        verify(orderRepository, times(1)).save(order);
        boolean compensationLogged = order.getStatusHistory().stream()
                .anyMatch(h -> h.getDetail().contains("Inventory compensation completed: 2 unit(s) restored"));
        assertTrue(compensationLogged, "Order history must record inventory restoration");

        // Verify Redis lock acquired and released in finally block
        verify(lock, times(1)).unlock();
    }
}
