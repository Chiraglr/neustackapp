package org.example.neustackapp.service;

import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.AddCartItemRequest;
import org.example.neustackapp.dto.CartItemResponse;
import org.example.neustackapp.dto.CartResponse;
import org.example.neustackapp.exception.InsufficientInventoryException;
import org.example.neustackapp.exception.InvalidCartOperationException;
import org.example.neustackapp.exception.ResourceNotFoundException;
import org.example.neustackapp.model.Cart;
import org.example.neustackapp.model.CartItem;
import org.example.neustackapp.model.CartItemId;
import org.example.neustackapp.model.Inventory;
import org.example.neustackapp.model.Product;
import org.example.neustackapp.repository.CartItemRepository;
import org.example.neustackapp.repository.CartRepository;
import org.example.neustackapp.repository.InventoryRepository;
import org.example.neustackapp.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public CartResponse createCart(AddCartItemRequest initialItem) {
        Cart cart = Cart.builder()
                .createdAt(LocalDateTime.now())
                .build();
        Cart savedCart = cartRepository.save(cart);

        if (initialItem != null && initialItem.getProductId() != null) {
            addItemToCart(savedCart.getId(), initialItem);
        }

        return getCart(savedCart.getId());
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart with id " + cartId + " not found."));

        List<CartItem> cartItems = cartItemRepository.findByIdCartId(cartId);
        if (cartItems.isEmpty()) {
            return CartResponse.builder()
                    .id(cart.getId())
                    .createdAt(cart.getCreatedAt())
                    .items(Collections.emptyList())
                    .totalPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .build();
        }

        List<Integer> productIds = cartItems.stream()
                .map(item -> item.getId().getProductId())
                .toList();

        Map<Integer, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        BigDecimal total = BigDecimal.ZERO;
        List<CartItemResponse> itemResponses = new ArrayList<>();

        for (CartItem item : cartItems) {
            Integer prodId = item.getId().getProductId();
            Product product = productMap.get(prodId);
            String name = product != null ? product.getName() : "Unknown Product";
            BigDecimal unitPrice = product != null && product.getCurrentUnitPrice() != null
                    ? product.getCurrentUnitPrice()
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            total = total.add(lineTotal);

            itemResponses.add(CartItemResponse.builder()
                    .productId(prodId)
                    .name(name)
                    .quantity(item.getQuantity())
                    .unitPrice(unitPrice)
                    .totalPrice(lineTotal)
                    .build());
        }

        return CartResponse.builder()
                .id(cart.getId())
                .createdAt(cart.getCreatedAt())
                .items(itemResponses)
                .totalPrice(total.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    @Transactional
    public CartResponse addItemToCart(UUID cartId, AddCartItemRequest request) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart with id " + cartId + " not found."));

        if (request == null || request.getProductId() == null) {
            throw new InvalidCartOperationException("Product id is required.");
        }

        int quantityToAdd = (request.getQuantity() != null) ? request.getQuantity() : 1;
        if (quantityToAdd <= 0) {
            throw new InvalidCartOperationException("Quantity must be greater than 0.");
        }

        Integer productId = request.getProductId();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + productId + " not found."));

        Optional<CartItem> existingCartItem = cartItemRepository.findByIdCartIdAndIdProductId(cartId, productId);
        int currentQuantityInCart = existingCartItem.map(CartItem::getQuantity).orElse(0);
        int targetQuantity = currentQuantityInCart + quantityToAdd;

        Inventory inventory = inventoryRepository.findById(productId).orElse(null);
        int availableStock = inventory != null ? inventory.getAvailableInventory() : 0;

        if (availableStock < targetQuantity) {
            throw new InsufficientInventoryException(
                    "Insufficient inventory for product '" + product.getName() + "' (id: " + productId +
                            "). Available: " + availableStock + ", Requested total: " + targetQuantity
            );
        }

        if (existingCartItem.isPresent()) {
            CartItem item = existingCartItem.get();
            item.setQuantity(targetQuantity);
            cartItemRepository.save(item);
        } else {
            CartItem newItem = CartItem.builder()
                    .id(new CartItemId(cartId, productId))
                    .quantity(targetQuantity)
                    .build();
            cartItemRepository.save(newItem);
        }

        return getCart(cartId);
    }

    @Transactional
    public CartResponse removeItemFromCart(UUID cartId, Integer productId) {
        if (!cartRepository.existsById(cartId)) {
            throw new ResourceNotFoundException("Cart with id " + cartId + " not found.");
        }

        CartItem cartItem = cartItemRepository.findByIdCartIdAndIdProductId(cartId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product with id " + productId + " not found in cart " + cartId
                ));

        if (cartItem.getQuantity() > 1) {
            cartItem.setQuantity(cartItem.getQuantity() - 1);
            cartItemRepository.save(cartItem);
        } else {
            cartItemRepository.delete(cartItem);
        }

        return getCart(cartId);
    }
}
