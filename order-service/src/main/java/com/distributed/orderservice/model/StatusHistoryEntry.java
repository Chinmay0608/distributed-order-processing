package com.distributed.orderservice.model;

import java.time.Instant;

public class StatusHistoryEntry {
    private String status;
    private Instant timestamp;
    private String detail;

    public StatusHistoryEntry() {
    }

    public StatusHistoryEntry(String status, Instant timestamp, String detail) {
        this.status = status;
        this.timestamp = timestamp;
        this.detail = detail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
