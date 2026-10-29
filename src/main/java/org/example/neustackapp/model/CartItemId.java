package org.example.neustackapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemId implements Serializable {

    @Column(name = "cart_id")
    private UUID cartId;

    @Column(name = "product_id")
    private Integer productId;
}
