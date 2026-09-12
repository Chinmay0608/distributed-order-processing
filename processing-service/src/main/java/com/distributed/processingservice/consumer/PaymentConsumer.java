package com.distributed.processingservice.consumer;

import com.distributed.processingservice.event.OrderPlacedEvent;
import com.distributed.processingservice.service.PaymentProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);
    private final PaymentProcessor paymentProcessor;

    public PaymentConsumer(PaymentProcessor paymentProcessor) {
        this.paymentProcessor = paymentProcessor;
    }

    @KafkaListener(topics = "order-placed", groupId = "payment-workers")
    public void consumeOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlacedEvent from topic 'order-placed' for orderId='{}'", event.getOrderId());
        try {
            paymentProcessor.processPayment(event);
        } catch (Exception e) {
            log.error("Error processing payment for orderId='{}': {}", event.getOrderId(), e.getMessage(), e);
        }
    }
}
