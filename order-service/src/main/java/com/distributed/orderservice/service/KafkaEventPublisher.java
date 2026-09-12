package com.distributed.orderservice.service;

import com.distributed.orderservice.event.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private static final String TOPIC_ORDER_PLACED = "order-placed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderPlaced(OrderPlacedEvent event) {
        try {
            log.info("Publishing OrderPlacedEvent for orderId='{}' to topic='{}'", event.getOrderId(), TOPIC_ORDER_PLACED);
            kafkaTemplate.send(TOPIC_ORDER_PLACED, event.getOrderId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish OrderPlacedEvent for orderId='{}': {}", event.getOrderId(), ex.getMessage());
                        } else {
                            log.info("Successfully delivered OrderPlacedEvent for orderId='{}' offset={}",
                                    event.getOrderId(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Exception occurred while sending event to Kafka: {}", e.getMessage(), e);
        }
    }
}
