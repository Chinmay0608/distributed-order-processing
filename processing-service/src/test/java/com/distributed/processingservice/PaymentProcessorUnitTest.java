package com.distributed.processingservice;

import com.distributed.processingservice.event.OrderPaymentFailedEvent;
import com.distributed.processingservice.event.OrderPaymentProcessedEvent;
import com.distributed.processingservice.event.OrderPlacedEvent;
import com.distributed.processingservice.model.Order;
import com.distributed.processingservice.model.OrderStatus;
import com.distributed.processingservice.producer.ProcessingEventPublisher;
import com.distributed.processingservice.repository.OrderRepository;
import com.distributed.processingservice.service.PaymentProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentProcessorUnitTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessingEventPublisher eventPublisher;

    private PaymentProcessor paymentProcessor;

    @BeforeEach
    void setUp() {
        paymentProcessor = new PaymentProcessor(orderRepository, eventPublisher);
        // Set delay to 0 for instant unit test execution
        ReflectionTestUtils.setField(paymentProcessor, "simulationDelayMs", 0L);
    }

    @Test
    void testPaymentProcessing_Success() {
        String orderId = "ORD-TEST-001";
        Order order = new Order(orderId, "key-1", "prod-1", "Standard Product", 2, 50.0, 100.0, OrderStatus.PLACED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderPlacedEvent event = new OrderPlacedEvent("evt-1", orderId, "prod-1", 2, 100.0);
        paymentProcessor.processPayment(event);

        assertEquals(OrderStatus.PAYMENT_PROCESSED, order.getStatus());
        verify(orderRepository, times(1)).save(order);

        ArgumentCaptor<OrderPaymentProcessedEvent> captor = ArgumentCaptor.forClass(OrderPaymentProcessedEvent.class);
        verify(eventPublisher, times(1)).publishPaymentProcessed(captor.capture());
        assertEquals(orderId, captor.getValue().getOrderId());
        verify(eventPublisher, never()).publishPaymentFailed(any());
    }

    @Test
    void testPaymentProcessing_DeterministicFailureTrigger_ByProductId() {
        String orderId = "ORD-FAIL-001";
        Order order = new Order(orderId, "key-2", "prod-fail-payment", "Simulated Payment Fail Item", 1, 99.99, 99.99, OrderStatus.PLACED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderPlacedEvent event = new OrderPlacedEvent("evt-2", orderId, "prod-fail-payment", 1, 99.99);
        paymentProcessor.processPayment(event);

        assertEquals(OrderStatus.FAILED, order.getStatus());
        verify(orderRepository, times(1)).save(order);

        ArgumentCaptor<OrderPaymentFailedEvent> captor = ArgumentCaptor.forClass(OrderPaymentFailedEvent.class);
        verify(eventPublisher, times(1)).publishPaymentFailed(captor.capture());
        assertEquals(orderId, captor.getValue().getOrderId());
        verify(eventPublisher, never()).publishPaymentProcessed(any());
    }

    @Test
    void testPaymentProcessing_DeterministicFailureTrigger_ByQuantity() {
        String orderId = "ORD-FAIL-002";
        Order order = new Order(orderId, "key-3", "prod-regular", "Regular Product", 99, 10.0, 990.0, OrderStatus.PLACED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderPlacedEvent event = new OrderPlacedEvent("evt-3", orderId, "prod-regular", 99, 990.0);
        paymentProcessor.processPayment(event);

        assertEquals(OrderStatus.FAILED, order.getStatus());
        verify(orderRepository, times(1)).save(order);

        verify(eventPublisher, times(1)).publishPaymentFailed(any(OrderPaymentFailedEvent.class));
        verify(eventPublisher, never()).publishPaymentProcessed(any());
    }
}
