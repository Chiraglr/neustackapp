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
public class ProductInventoryResponse {

    private Integer id;

    private String name;

    @JsonProperty("current_unit_price")
    private BigDecimal currentUnitPrice;

    @JsonProperty("available_inventory")
    private Integer availableInventory;
}
