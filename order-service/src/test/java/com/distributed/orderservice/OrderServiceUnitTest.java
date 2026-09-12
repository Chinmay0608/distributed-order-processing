package com.distributed.orderservice;

import com.distributed.orderservice.dto.request.PlaceOrderRequest;
import com.distributed.orderservice.dto.response.OrderResponse;
import com.distributed.orderservice.event.OrderPlacedEvent;
import com.distributed.orderservice.exception.LockAcquisitionException;
import com.distributed.orderservice.exception.OutOfStockException;
import com.distributed.orderservice.model.IdempotencyRecord;
import com.distributed.orderservice.model.IdempotencyStatus;
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
import org.redisson.api.RLock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceUnitTest {

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

    @Mock
    private RLock lock;

    private OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    }

    @Test
    void testPlaceOrder_Success() {
        String productId = "prod-101";
        String key = "idemp-key-101";
        PlaceOrderRequest request = new PlaceOrderRequest(productId, 1);
        Product product = new Product(productId, "Noise Cancelling Headphones", "Desc", 299.99, 5);

        when(idempotencyService.computePayloadHash(productId, 1)).thenReturn("hash123");
        when(idempotencyService.checkOrStart(key, "hash123")).thenReturn(Optional.empty());
        when(inventoryLockService.acquireLock(productId)).thenReturn(lock);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class))).thenReturn(updateResult);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.placeOrder(request, key);

        assertNotNull(response);
        assertEquals(productId, response.getProductId());
        assertEquals("PLACED", response.getStatus());
        assertEquals(299.99, response.getTotalAmount());

        verify(inventoryLockService, times(1)).releaseLock(lock);
        verify(idempotencyService, times(1)).markCompleted(eq(key), anyString(), eq(201), any());
        verify(kafkaEventPublisher, times(1)).publishOrderPlaced(any(OrderPlacedEvent.class));
    }

    @Test
    void testPlaceOrder_OutOfStock() {
        String productId = "prod-102";
        String key = "idemp-key-102";
        PlaceOrderRequest request = new PlaceOrderRequest(productId, 2);
        Product product = new Product(productId, "Mechanical Keyboard", "Desc", 99.99, 1); // Only 1 in stock

        when(idempotencyService.computePayloadHash(productId, 2)).thenReturn("hash456");
        when(idempotencyService.checkOrStart(key, "hash456")).thenReturn(Optional.empty());
        when(inventoryLockService.acquireLock(productId)).thenReturn(lock);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThrows(OutOfStockException.class, () -> orderService.placeOrder(request, key));

        verify(inventoryLockService, times(1)).releaseLock(lock);
        verify(idempotencyService, times(1)).markFailed(key);
        verify(orderRepository, never()).save(any());
        verify(kafkaEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    void testPlaceOrder_LockAcquisitionFailed() {
        String productId = "prod-103";
        String key = "idemp-key-103";
        PlaceOrderRequest request = new PlaceOrderRequest(productId, 1);

        when(idempotencyService.computePayloadHash(productId, 1)).thenReturn("hash789");
        when(idempotencyService.checkOrStart(key, "hash789")).thenReturn(Optional.empty());
        when(inventoryLockService.acquireLock(productId))
                .thenThrow(new LockAcquisitionException("High contention on product inventory. Please retry."));

        assertThrows(LockAcquisitionException.class, () -> orderService.placeOrder(request, key));

        verify(idempotencyService, times(1)).markFailed(key);
        verify(productRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testPlaceOrder_InvalidQuantityReturns400() {
        PlaceOrderRequest request = new PlaceOrderRequest("prod-104", 0);
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(request, "valid-key"));
    }

    @Test
    void testPlaceOrder_MissingIdempotencyKeyReturns400() {
        PlaceOrderRequest request = new PlaceOrderRequest("prod-105", 1);
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(request, null));
    }
}
