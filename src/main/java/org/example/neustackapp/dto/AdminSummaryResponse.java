package org.example.neustackapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSummaryResponse {

    @JsonProperty("products_sold")
    private List<ProductSalesSummary> productsSold;

    @JsonProperty("gross_revenue")
    private BigDecimal grossRevenue;

    @JsonProperty("total_discounts")
    private BigDecimal totalDiscounts;

    @JsonProperty("net_revenue")
    private BigDecimal netRevenue;

    @JsonProperty("coupons_generated")
    private long couponsGenerated;

    @JsonProperty("coupons_available")
    private long couponsAvailable;

    @JsonProperty("coupons_redeemed")
    private long couponsRedeemed;

    @JsonProperty("total_orders")
    private long totalOrders;
}
