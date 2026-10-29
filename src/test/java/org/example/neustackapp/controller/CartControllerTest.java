package org.example.neustackapp.controller;

import org.example.neustackapp.dto.AddCartItemRequest;
import org.example.neustackapp.dto.CartItemResponse;
import org.example.neustackapp.dto.CartResponse;
import org.example.neustackapp.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    private UUID cartId;
    private CartResponse sampleCartResponse;

    @BeforeEach
    void setUp() {
        cartId = UUID.randomUUID();
        sampleCartResponse = CartResponse.builder()
                .id(cartId)
                .createdAt(LocalDateTime.now())
                .items(List.of(
                        CartItemResponse.builder()
                                .productId(1)
                                .name("Samsung")
                                .quantity(2)
                                .unitPrice(new BigDecimal("1000.00"))
                                .totalPrice(new BigDecimal("2000.00"))
                                .build()
                ))
                .totalPrice(new BigDecimal("2000.00"))
                .build();
    }

    @Test
    void createCart_returns201Created() {
        when(cartService.createCart(any())).thenReturn(sampleCartResponse);

        ResponseEntity<CartResponse> response = cartController.createCart(new AddCartItemRequest(1, 2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(cartId);
        verify(cartService).createCart(any());
    }

    @Test
    void getCart_returns200Ok() {
        when(cartService.getCart(cartId)).thenReturn(sampleCartResponse);

        ResponseEntity<CartResponse> response = cartController.getCart(cartId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(sampleCartResponse);
        verify(cartService).getCart(cartId);
    }

    @Test
    void addItemToCart_returns200Ok() {
        when(cartService.addItemToCart(eq(cartId), any())).thenReturn(sampleCartResponse);

        ResponseEntity<CartResponse> response = cartController.addItemToCart(cartId, new AddCartItemRequest(1, 2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(sampleCartResponse);
        verify(cartService).addItemToCart(eq(cartId), any());
    }

    @Test
    void removeItemFromCart_returns200Ok() {
        when(cartService.removeItemFromCart(cartId, 1)).thenReturn(sampleCartResponse);

        ResponseEntity<CartResponse> response = cartController.removeItemFromCart(cartId, 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(sampleCartResponse);
        verify(cartService).removeItemFromCart(cartId, 1);
    }
}
