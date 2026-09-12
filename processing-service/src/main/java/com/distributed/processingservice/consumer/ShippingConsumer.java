package com.distributed.processingservice.consumer;

import com.distributed.processingservice.event.OrderPaymentProcessedEvent;
import com.distributed.processingservice.service.ShippingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ShippingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShippingConsumer.class);
    private final ShippingProcessor shippingProcessor;

    public ShippingConsumer(ShippingProcessor shippingProcessor) {
        this.shippingProcessor = shippingProcessor;
    }

    @KafkaListener(topics = "order-payment-processed", groupId = "shipping-workers")
    public void consumePaymentProcessed(OrderPaymentProcessedEvent event) {
        log.info("Received OrderPaymentProcessedEvent from topic 'order-payment-processed' for orderId='{}'", event.getOrderId());
        try {
            shippingProcessor.processShipping(event);
        } catch (Exception e) {
            log.error("Error processing shipping for orderId='{}': {}", event.getOrderId(), e.getMessage(), e);
        }
    }
}
