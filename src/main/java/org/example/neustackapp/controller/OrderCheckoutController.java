package org.example.neustackapp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.CheckoutRequest;
import org.example.neustackapp.dto.OrderResponse;
import org.example.neustackapp.service.OrderCheckoutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderCheckoutController {

    private final OrderCheckoutService orderCheckoutService;

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        OrderResponse orderResponse = orderCheckoutService.checkout(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderResponse);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        OrderResponse orderResponse = orderCheckoutService.getOrder(orderId);
        return ResponseEntity.ok(orderResponse);
    }
}
