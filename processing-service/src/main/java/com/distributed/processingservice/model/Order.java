package com.distributed.processingservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    @Indexed
    private String productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
    private Double totalAmount;

    @Indexed
    private OrderStatus status;

    private List<StatusHistoryEntry> statusHistory = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    public Order() {
    }

    public Order(String orderId, String idempotencyKey, String productId, String productName,
                 Integer quantity, Double unitPrice, Double totalAmount, OrderStatus status) {
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.statusHistory.add(new StatusHistoryEntry(status.name(), Instant.now(), "Order placed and stock reserved"));
    }

    public void addStatusHistory(OrderStatus newStatus, String detail) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
        this.statusHistory.add(new StatusHistoryEntry(newStatus.name(), this.updatedAt, detail));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<StatusHistoryEntry> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<StatusHistoryEntry> statusHistory) {
        this.statusHistory = statusHistory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
