package org.example.neustackapp.service;

import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.AdminSummaryResponse;
import org.example.neustackapp.dto.ProductSalesSummary;
import org.example.neustackapp.model.Order;
import org.example.neustackapp.model.OrderItem;
import org.example.neustackapp.model.Product;
import org.example.neustackapp.repository.DiscountCouponRepository;
import org.example.neustackapp.repository.OrderItemRepository;
import org.example.neustackapp.repository.OrderRepository;
import org.example.neustackapp.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final DiscountCouponRepository discountCouponRepository;

    @Transactional(readOnly = true)
    public AdminSummaryResponse getAdminSummary() {
        List<Order> allOrders = orderRepository.findAll();
        List<OrderItem> allOrderItems = orderItemRepository.findAll();

        // --- Products sold: group by productId, sum quantities ---
        Map<Integer, Long> qtySoldByProduct = allOrderItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getId().getProductId(),
                        Collectors.summingLong(OrderItem::getQuantity)));

        // Fetch product names for those IDs
        List<Product> products = productRepository.findByIdIn(qtySoldByProduct.keySet().stream().toList());
        Map<Integer, String> productNameById = products.stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));

        List<ProductSalesSummary> productsSold = qtySoldByProduct.entrySet().stream()
                .map(entry -> ProductSalesSummary.builder()
                        .productId(entry.getKey())
                        .productName(productNameById.getOrDefault(entry.getKey(), "Unknown"))
                        .quantitySold(entry.getValue())
                        .build())
                .toList();

        // --- Gross revenue: sum(quantity * purchasedUnitPrice) across all order items ---
        BigDecimal grossRevenue = allOrderItems.stream()
                .map(item -> item.getPurchasedUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- Net revenue: sum of order.totalAmount (already post-discount) ---
        BigDecimal netRevenue = allOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- Total discounts granted: sum of order.discountAmount from orders table ---
        BigDecimal totalDiscounts = allOrders.stream()
                .map(order -> order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // --- Coupon stats ---
        long couponsAvailable = discountCouponRepository.countByIsUsed(false);
        long couponsRedeemed = discountCouponRepository.countByIsUsed(true);
        long couponsGenerated = couponsAvailable + couponsRedeemed;

        // --- Total orders ---
        long totalOrders = allOrders.size();

        return AdminSummaryResponse.builder()
                .productsSold(productsSold)
                .grossRevenue(grossRevenue)
                .totalDiscounts(totalDiscounts)
                .netRevenue(netRevenue)
                .couponsGenerated(couponsGenerated)
                .couponsAvailable(couponsAvailable)
                .couponsRedeemed(couponsRedeemed)
                .totalOrders(totalOrders)
                .build();
    }
}
