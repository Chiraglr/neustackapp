package org.example.neustackapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.neustackapp.model.DiscountCoupon;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponResponse {

    private Integer id;

    private String code;

    @JsonProperty("discount_percentage")
    private Integer discountPercentage;

    @JsonProperty("max_limit")
    private Integer maxLimit;

    @JsonProperty("is_used")
    private Boolean isUsed;

    public static CouponResponse fromEntity(DiscountCoupon coupon) {
        if (coupon == null) {
            return null;
        }
        return CouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .discountPercentage(coupon.getDiscountPercentage())
                .maxLimit(coupon.getMaxLimit())
                .isUsed(coupon.getIsUsed())
                .build();
    }
}
