package com.distributed.orderservice.exception;

public class OrderAlreadyProcessingException extends RuntimeException {
    public OrderAlreadyProcessingException(String message) {
        super(message);
    }
}
