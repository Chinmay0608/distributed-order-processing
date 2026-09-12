package com.distributed.processingservice.event;

import java.time.Instant;

public class OrderShippedEvent {
    private String eventId;
    private String eventType;
    private String orderId;
    private String trackingNumber;
    private Instant timestamp;

    public OrderShippedEvent() {
    }

    public OrderShippedEvent(String eventId, String orderId, String trackingNumber) {
        this.eventId = eventId;
        this.eventType = "ORDER_SHIPPED";
        this.orderId = orderId;
        this.trackingNumber = trackingNumber;
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

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
