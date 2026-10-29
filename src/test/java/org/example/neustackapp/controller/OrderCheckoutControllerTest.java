package org.example.neustackapp.controller;

import org.example.neustackapp.dto.CheckoutRequest;
import org.example.neustackapp.dto.OrderResponse;
import org.example.neustackapp.service.OrderCheckoutService;
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
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutControllerTest {

    @Mock
    private OrderCheckoutService orderCheckoutService;

    @InjectMocks
    private OrderCheckoutController orderCheckoutController;

    private UUID orderId;
    private OrderResponse sampleOrderResponse;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        sampleOrderResponse = OrderResponse.builder()
                .orderId(orderId)
                .createdAt(LocalDateTime.now())
                .items(Collections.emptyList())
                .grossAmount(new BigDecimal("1000.00"))
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1000.00"))
                .build();
    }

    @Test
    void checkout_returns201Created() {
        when(orderCheckoutService.checkout(any(CheckoutRequest.class))).thenReturn(sampleOrderResponse);

        CheckoutRequest request = new CheckoutRequest(UUID.randomUUID(), null);
        ResponseEntity<OrderResponse> response = orderCheckoutController.checkout(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrderId()).isEqualTo(orderId);
        verify(orderCheckoutService).checkout(any(CheckoutRequest.class));
    }

    @Test
    void getOrder_returns200Ok() {
        when(orderCheckoutService.getOrder(orderId)).thenReturn(sampleOrderResponse);

        ResponseEntity<OrderResponse> response = orderCheckoutController.getOrder(orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(sampleOrderResponse);
        verify(orderCheckoutService).getOrder(orderId);
    }
}
