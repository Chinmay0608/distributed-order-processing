package com.distributed.processingservice;

import com.distributed.processingservice.event.OrderPaymentProcessedEvent;
import com.distributed.processingservice.event.OrderShippedEvent;
import com.distributed.processingservice.model.Order;
import com.distributed.processingservice.model.OrderStatus;
import com.distributed.processingservice.producer.ProcessingEventPublisher;
import com.distributed.processingservice.repository.OrderRepository;
import com.distributed.processingservice.service.ShippingProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShippingProcessorUnitTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProcessingEventPublisher eventPublisher;

    private ShippingProcessor shippingProcessor;

    @BeforeEach
    void setUp() {
        shippingProcessor = new ShippingProcessor(orderRepository, eventPublisher);
        ReflectionTestUtils.setField(shippingProcessor, "simulationDelayMs", 0L);
    }

    @Test
    void testShippingProcessing_Success() {
        String orderId = "ORD-SHIP-001";
        Order order = new Order(orderId, "key-4", "prod-2", "Keyboard", 1, 89.99, 89.99, OrderStatus.PAYMENT_PROCESSED);
        when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

        OrderPaymentProcessedEvent event = new OrderPaymentProcessedEvent("evt-4", orderId, "prod-2", 1, 89.99);
        shippingProcessor.processShipping(event);

        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        verify(orderRepository, times(1)).save(order);

        ArgumentCaptor<OrderShippedEvent> captor = ArgumentCaptor.forClass(OrderShippedEvent.class);
        verify(eventPublisher, times(1)).publishOrderShipped(captor.capture());
        assertEquals(orderId, captor.getValue().getOrderId());
        assertTrue(captor.getValue().getTrackingNumber().startsWith("TRK-"));
    }
}
