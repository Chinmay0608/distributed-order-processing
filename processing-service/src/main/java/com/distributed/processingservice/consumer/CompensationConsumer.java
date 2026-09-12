package com.distributed.processingservice.consumer;

import com.distributed.processingservice.event.OrderPaymentFailedEvent;
import com.distributed.processingservice.service.InventoryCompensationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CompensationConsumer {

    private static final Logger log = LoggerFactory.getLogger(CompensationConsumer.class);
    private final InventoryCompensationService compensationService;

    public CompensationConsumer(InventoryCompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @KafkaListener(topics = "order-payment-failed", groupId = "compensation-workers")
    public void consumePaymentFailed(OrderPaymentFailedEvent event) {
        log.warn("Received OrderPaymentFailedEvent from topic 'order-payment-failed' for orderId='{}'", event.getOrderId());
        try {
            compensationService.compensateInventory(event);
        } catch (Exception e) {
            log.error("Error compensating inventory for orderId='{}': {}", event.getOrderId(), e.getMessage(), e);
        }
    }
}
