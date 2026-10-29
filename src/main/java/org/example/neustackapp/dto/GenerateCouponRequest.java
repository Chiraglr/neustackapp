package org.example.neustackapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateCouponRequest {

    @NotBlank(message = "code must not be blank")
    private String code;

    @NotNull(message = "discount_percentage must not be null")
    @Min(value = 1, message = "discount_percentage must be at least 1")
    @Max(value = 100, message = "discount_percentage must not exceed 100")
    @JsonProperty("discount_percentage")
    private Integer discountPercentage;

    @NotNull(message = "max_limit must not be null")
    @Min(value = 1, message = "max_limit must be at least 1")
    @JsonProperty("max_limit")
    private Integer maxLimit;
}
