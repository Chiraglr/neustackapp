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
public class OrderResponse {

    @JsonProperty("order_id")
    private UUID orderId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private List<OrderItemResponse> items;

    @JsonProperty("gross_amount")
    private BigDecimal grossAmount;

    @JsonProperty("discount_amount")
    private BigDecimal discountAmount;

    @JsonProperty("discount_coupon_id")
    private Integer discountCouponId;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;
}
