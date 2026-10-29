package org.example.neustackapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    @JsonProperty("product_id")
    private Integer productId;

    private String name;

    private Integer quantity;

    @JsonProperty("purchased_unit_price")
    private BigDecimal purchasedUnitPrice;

    @JsonProperty("total_price")
    private BigDecimal totalPrice;
}
