package org.example.neustackapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("order_id")
    private UUID orderId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private List<CartItemResponse> items;

    @JsonProperty("total_price")
    private BigDecimal totalPrice;
}
