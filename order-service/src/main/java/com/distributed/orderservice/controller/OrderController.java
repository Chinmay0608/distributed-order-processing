package com.distributed.orderservice.controller;

import com.distributed.orderservice.dto.request.PlaceOrderRequest;
import com.distributed.orderservice.dto.response.OrderResponse;
import com.distributed.orderservice.dto.response.OrderStatusResponse;
import com.distributed.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        OrderResponse response = orderService.placeOrder(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderStatusResponse> getOrderStatus(@PathVariable String orderId) {
        OrderStatusResponse response = orderService.getOrderStatus(orderId);
        return ResponseEntity.ok(response);
    }
}
