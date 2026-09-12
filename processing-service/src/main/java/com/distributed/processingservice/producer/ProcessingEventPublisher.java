package com.distributed.processingservice.producer;

import com.distributed.processingservice.event.OrderPaymentFailedEvent;
import com.distributed.processingservice.event.OrderPaymentProcessedEvent;
import com.distributed.processingservice.event.OrderShippedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProcessingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProcessingEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ProcessingEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentProcessed(OrderPaymentProcessedEvent event) {
        log.info("Emitting OrderPaymentProcessedEvent for orderId='{}'", event.getOrderId());
        kafkaTemplate.send("order-payment-processed", event.getOrderId(), event);
    }

    public void publishPaymentFailed(OrderPaymentFailedEvent event) {
        log.info("Emitting OrderPaymentFailedEvent for orderId='{}'", event.getOrderId());
        kafkaTemplate.send("order-payment-failed", event.getOrderId(), event);
    }

    public void publishOrderShipped(OrderShippedEvent event) {
        log.info("Emitting OrderShippedEvent for orderId='{}'", event.getOrderId());
        kafkaTemplate.send("order-shipped", event.getOrderId(), event);
    }
}
