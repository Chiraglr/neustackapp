package org.example.neustackapp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.AddCartItemRequest;
import org.example.neustackapp.dto.CartResponse;
import org.example.neustackapp.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // --- Standard RESTful API ---

    @PostMapping("/carts")
    public ResponseEntity<CartResponse> createCart(@RequestBody(required = false) @Valid AddCartItemRequest request) {
        CartResponse cart = cartService.createCart(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    @GetMapping("/carts/{cartId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable UUID cartId) {
        CartResponse cart = cartService.getCart(cartId);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/carts/{cartId}/items")
    public ResponseEntity<CartResponse> addItemToCart(
            @PathVariable UUID cartId,
            @RequestBody @Valid AddCartItemRequest request) {
        CartResponse cart = cartService.addItemToCart(cartId, request);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/carts/{cartId}/items/{productId}")
    public ResponseEntity<CartResponse> removeItemFromCart(
            @PathVariable UUID cartId,
            @PathVariable Integer productId) {
        CartResponse cart = cartService.removeItemFromCart(cartId, productId);
        return ResponseEntity.ok(cart);
    }
}
