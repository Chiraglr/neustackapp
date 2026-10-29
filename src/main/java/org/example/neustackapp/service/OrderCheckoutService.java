package org.example.neustackapp.service;

import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.CheckoutRequest;
import org.example.neustackapp.dto.OrderItemResponse;
import org.example.neustackapp.dto.OrderResponse;
import org.example.neustackapp.exception.InsufficientInventoryException;
import org.example.neustackapp.exception.InvalidCartOperationException;
import org.example.neustackapp.exception.ResourceNotFoundException;
import org.example.neustackapp.model.*;
import org.example.neustackapp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderCheckoutService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final DiscountCouponRepository discountCouponRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        if (request == null || request.getCartId() == null) {
            throw new InvalidCartOperationException("cart_id is required for checkout.");
        }

        UUID cartId = request.getCartId();
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart with id " + cartId + " not found."));

        List<CartItem> cartItems = cartItemRepository.findByIdCartId(cartId);
        if (cartItems.isEmpty()) {
            throw new InvalidCartOperationException("Cannot checkout an empty cart.");
        }

        DiscountCoupon coupon = null;
        if (request.getCouponId() != null) {
            Integer couponId = request.getCouponId();
            coupon = discountCouponRepository.findByIdForUpdate(couponId)
                    .orElseThrow(() -> new ResourceNotFoundException("Coupon with id " + couponId + " not found."));

            if (Boolean.TRUE.equals(coupon.getIsUsed())) {
                throw new InvalidCartOperationException("Coupon with id " + couponId + " has already been used.");
            }
        }

        // Sort items by productId to enforce a canonical lock acquisition order (prevents deadlocks)
        List<CartItem> sortedItems = cartItems.stream()
                .sorted(Comparator.comparing(item -> item.getId().getProductId()))
                .toList();

        List<Integer> productIds = sortedItems.stream()
                .map(item -> item.getId().getProductId())
                .toList();

        Map<Integer, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Deduct inventory under pessimistic write locks
        for (CartItem item : sortedItems) {
            Integer productId = item.getId().getProductId();
            Product product = productMap.get(productId);
            String productName = product != null ? product.getName() : "ID " + productId;

            Inventory inventory = inventoryRepository.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory for product " + productId + " not found."));

            if (inventory.getAvailableInventory() < item.getQuantity()) {
                throw new InsufficientInventoryException(
                        "Insufficient inventory for product '" + productName + "' (id: " + productId +
                                "). Available: " + inventory.getAvailableInventory() + ", Requested: " + item.getQuantity()
                );
            }

            inventory.setAvailableInventory(inventory.getAvailableInventory() - item.getQuantity());
            inventoryRepository.save(inventory);
        }

        // Calculate line items and gross total
        BigDecimal grossTotal = BigDecimal.ZERO;
        List<OrderItemResponse> itemResponses = new ArrayList<>();

        for (CartItem item : sortedItems) {
            Integer productId = item.getId().getProductId();
            Product product = productMap.get(productId);
            BigDecimal unitPrice = product != null && product.getCurrentUnitPrice() != null
                    ? product.getCurrentUnitPrice()
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            grossTotal = grossTotal.add(lineTotal);

            itemResponses.add(OrderItemResponse.builder()
                    .productId(productId)
                    .name(product != null ? product.getName() : "Unknown Product")
                    .quantity(item.getQuantity())
                    .purchasedUnitPrice(unitPrice)
                    .totalPrice(lineTotal)
                    .build());
        }

        grossTotal = grossTotal.setScale(2, RoundingMode.HALF_UP);

        // Calculate discount and net total
        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Integer appliedCouponId = null;

        if (coupon != null) {
            appliedCouponId = coupon.getId();
            BigDecimal discountPercent = BigDecimal.valueOf(coupon.getDiscountPercentage())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal calculatedDiscount = grossTotal.multiply(discountPercent)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal maxLimit = BigDecimal.valueOf(coupon.getMaxLimit())
                    .setScale(2, RoundingMode.HALF_UP);

            discountAmount = calculatedDiscount.min(maxLimit);
            coupon.setIsUsed(true);
            discountCouponRepository.save(coupon);
        }

        BigDecimal netTotal = grossTotal.subtract(discountAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        // Persist order
        Order order = Order.builder()
                .orderId(UUID.randomUUID())
                .totalAmount(netTotal)
                .discountCodeApplied(appliedCouponId)
                .createdAt(LocalDateTime.now())
                .build();
        Order savedOrder = orderRepository.save(order);

        // Persist order items
        for (CartItem item : sortedItems) {
            Integer productId = item.getId().getProductId();
            Product product = productMap.get(productId);
            BigDecimal unitPrice = product != null && product.getCurrentUnitPrice() != null
                    ? product.getCurrentUnitPrice()
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            OrderItem orderItem = OrderItem.builder()
                    .id(new OrderItemId(savedOrder.getOrderId(), productId))
                    .quantity(item.getQuantity())
                    .purchasedUnitPrice(unitPrice)
                    .build();
            orderItemRepository.save(orderItem);
        }

        // Delete cart and its items
        cartItemRepository.deleteAll(cartItems);
        cartRepository.delete(cart);

        return OrderResponse.builder()
                .orderId(savedOrder.getOrderId())
                .createdAt(savedOrder.getCreatedAt())
                .items(itemResponses)
                .grossAmount(grossTotal)
                .discountAmount(discountAmount)
                .discountCouponId(appliedCouponId)
                .totalAmount(netTotal)
                .build();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found."));

        List<OrderItem> orderItems = orderItemRepository.findByIdOrderId(orderId);
        List<Integer> productIds = orderItems.stream()
                .map(item -> item.getId().getProductId())
                .toList();

        Map<Integer, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        BigDecimal grossTotal = BigDecimal.ZERO;
        List<OrderItemResponse> itemResponses = new ArrayList<>();

        for (OrderItem item : orderItems) {
            Integer productId = item.getId().getProductId();
            Product product = productMap.get(productId);
            BigDecimal unitPrice = item.getPurchasedUnitPrice() != null
                    ? item.getPurchasedUnitPrice()
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            grossTotal = grossTotal.add(lineTotal);

            itemResponses.add(OrderItemResponse.builder()
                    .productId(productId)
                    .name(product != null ? product.getName() : "Unknown Product")
                    .quantity(item.getQuantity())
                    .purchasedUnitPrice(unitPrice)
                    .totalPrice(lineTotal)
                    .build());
        }

        grossTotal = grossTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal netTotal = order.getTotalAmount() != null
                ? order.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                : grossTotal;
        BigDecimal discountAmount = grossTotal.subtract(netTotal)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .grossAmount(grossTotal)
                .discountAmount(discountAmount)
                .discountCouponId(order.getDiscountCodeApplied())
                .totalAmount(netTotal)
                .build();
    }
}
