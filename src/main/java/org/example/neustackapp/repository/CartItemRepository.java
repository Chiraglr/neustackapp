package org.example.neustackapp.repository;

import org.example.neustackapp.model.CartItem;
import org.example.neustackapp.model.CartItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    List<CartItem> findByIdCartId(UUID cartId);

    Optional<CartItem> findByIdCartIdAndIdProductId(UUID cartId, Integer productId);

    void deleteByIdCartId(UUID cartId);

    void deleteByIdCartIdAndIdProductId(UUID cartId, Integer productId);
}
