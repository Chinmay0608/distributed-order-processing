package com.distributed.processingservice.service;

import com.distributed.processingservice.event.OrderPaymentFailedEvent;
import com.distributed.processingservice.event.OrderPaymentProcessedEvent;
import com.distributed.processingservice.event.OrderPlacedEvent;
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
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final OrderRepository orderRepository;
    private final ProcessingEventPublisher eventPublisher;

    @Value("${simulation.delay-ms:1500}")
    private long simulationDelayMs;

    public PaymentProcessor(OrderRepository orderRepository, ProcessingEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public void processPayment(OrderPlacedEvent event) {
        log.info("Processing payment for orderId='{}', productId='{}'", event.getOrderId(), event.getProductId());

        // Simulate async payment gateway latency
        if (simulationDelayMs > 0) {
            try {
                Thread.sleep(simulationDelayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // Deterministic Payment Failure Trigger
        boolean shouldFail = "prod-fail-payment".equals(event.getProductId()) || (event.getQuantity() != null && event.getQuantity() == 99);

        Order order = orderRepository.findByOrderId(event.getOrderId()).orElse(null);

        if (shouldFail) {
            log.warn("Payment FAILED (deterministic trigger matched) for orderId='{}'", event.getOrderId());
            if (order != null) {
                order.addStatusHistory(OrderStatus.FAILED, "Payment declined by gateway: simulated decline");
                orderRepository.save(order);
            }

            OrderPaymentFailedEvent failedEvent = new OrderPaymentFailedEvent(
                    UUID.randomUUID().toString(),
                    event.getOrderId(),
                    event.getProductId(),
                    event.getQuantity(),
                    event.getTotalAmount(),
                    "Simulated payment gateway decline"
            );
            eventPublisher.publishPaymentFailed(failedEvent);

        } else {
            log.info("Payment SUCCESSFUL for orderId='{}'", event.getOrderId());
            if (order != null) {
                order.addStatusHistory(OrderStatus.PAYMENT_PROCESSED, "Payment captured successfully via gateway");
                orderRepository.save(order);
            }

            OrderPaymentProcessedEvent processedEvent = new OrderPaymentProcessedEvent(
                    UUID.randomUUID().toString(),
                    event.getOrderId(),
                    event.getProductId(),
                    event.getQuantity(),
                    event.getTotalAmount()
            );
            eventPublisher.publishPaymentProcessed(processedEvent);
        }
    }
}
