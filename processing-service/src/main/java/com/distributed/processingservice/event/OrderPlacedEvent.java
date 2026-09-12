package com.distributed.processingservice.event;

import java.time.Instant;

public class OrderPlacedEvent {
    private String eventId;
    private String eventType;
    private String orderId;
    private String productId;
    private Integer quantity;
    private Double totalAmount;
    private Instant timestamp;

    public OrderPlacedEvent() {
    }

    public OrderPlacedEvent(String eventId, String orderId, String productId, Integer quantity, Double totalAmount) {
        this.eventId = eventId;
        this.eventType = "ORDER_PLACED";
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.timestamp = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
