package org.example.neustackapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @EmbeddedId
    private OrderItemId id;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "purchased_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal purchasedUnitPrice;
}
