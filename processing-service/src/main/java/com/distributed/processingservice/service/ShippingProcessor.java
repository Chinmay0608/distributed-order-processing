package com.distributed.processingservice.service;

import com.distributed.processingservice.event.OrderPaymentProcessedEvent;
import com.distributed.processingservice.event.OrderShippedEvent;
import com.distributed.processingservice.model.Order;
import com.distributed.processingservice.model.OrderStatus;
import com.distributed.processingservice.producer.ProcessingEventPublisher;
import com.distributed.processingservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ShippingProcessor {

    private static final Logger log = LoggerFactory.getLogger(ShippingProcessor.class);

    private final OrderRepository orderRepository;
    private final ProcessingEventPublisher eventPublisher;

    @Value("${simulation.delay-ms:1500}")
    private long simulationDelayMs;

    public ShippingProcessor(OrderRepository orderRepository, ProcessingEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public void processShipping(OrderPaymentProcessedEvent event) {
        log.info("Processing shipping dispatch for orderId='{}'", event.getOrderId());

        if (simulationDelayMs > 0) {
            try {
                Thread.sleep(simulationDelayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        String trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Order order = orderRepository.findByOrderId(event.getOrderId()).orElse(null);
        if (order != null) {
            order.addStatusHistory(OrderStatus.SHIPPED, "Order dispatched via courier (Tracking: " + trackingNumber + ")");
            orderRepository.save(order);
        }

        OrderShippedEvent shippedEvent = new OrderShippedEvent(
                UUID.randomUUID().toString(),
                event.getOrderId(),
                trackingNumber
        );
        eventPublisher.publishOrderShipped(shippedEvent);
        log.info("Order '{}' successfully shipped with tracking '{}'", event.getOrderId(), trackingNumber);
    }
}
