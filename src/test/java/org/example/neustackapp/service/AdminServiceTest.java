package org.example.neustackapp.service;

import org.example.neustackapp.dto.AdminSummaryResponse;
import org.example.neustackapp.model.Order;
import org.example.neustackapp.model.OrderItem;
import org.example.neustackapp.model.OrderItemId;
import org.example.neustackapp.model.Product;
import org.example.neustackapp.repository.DiscountCouponRepository;
import org.example.neustackapp.repository.OrderItemRepository;
import org.example.neustackapp.repository.OrderRepository;
import org.example.neustackapp.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DiscountCouponRepository discountCouponRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void getAdminSummary_withOrdersAndItems_returnsCorrectSummary() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        // Orders: totalAmount is post-discount
        Order order1 = Order.builder()
                .orderId(orderId1)
                .totalAmount(new BigDecimal("90.00"))
                .createdAt(LocalDateTime.now())
                .build();

        Order order2 = Order.builder()
                .orderId(orderId2)
                .totalAmount(new BigDecimal("150.00"))
                .createdAt(LocalDateTime.now())
                .build();

        // Order items: product 1 ordered twice across different orders, product 2 once
        OrderItem item1 = OrderItem.builder()
                .id(new OrderItemId(orderId1, 1))
                .quantity(2)
                .purchasedUnitPrice(new BigDecimal("50.00"))
                .build(); // subtotal = 100.00

        OrderItem item2 = OrderItem.builder()
                .id(new OrderItemId(orderId2, 1))
                .quantity(1)
                .purchasedUnitPrice(new BigDecimal("50.00"))
                .build(); // subtotal = 50.00

        OrderItem item3 = OrderItem.builder()
                .id(new OrderItemId(orderId2, 2))
                .quantity(3)
                .purchasedUnitPrice(new BigDecimal("40.00"))
                .build(); // subtotal = 120.00

        // gross = 100 + 50 + 120 = 270.00
        // net   = 90 + 150 = 240.00
        // discount = 270 - 240 = 30.00

        Product product1 = Product.builder().id(1).name("Widget").currentUnitPrice(new BigDecimal("50.00")).build();
        Product product2 = Product.builder().id(2).name("Gadget").currentUnitPrice(new BigDecimal("40.00")).build();

        when(orderRepository.findAll()).thenReturn(List.of(order1, order2));
        when(orderItemRepository.findAll()).thenReturn(List.of(item1, item2, item3));
        when(productRepository.findByIdIn(List.of(1, 2))).thenReturn(List.of(product1, product2));
        when(discountCouponRepository.countByIsUsed(false)).thenReturn(5L);
        when(discountCouponRepository.countByIsUsed(true)).thenReturn(3L);

        AdminSummaryResponse result = adminService.getAdminSummary();

        assertThat(result.getGrossRevenue()).isEqualByComparingTo("270.00");
        assertThat(result.getNetRevenue()).isEqualByComparingTo("240.00");
        assertThat(result.getTotalDiscounts()).isEqualByComparingTo("30.00");

        assertThat(result.getCouponsGenerated()).isEqualTo(8L);
        assertThat(result.getCouponsAvailable()).isEqualTo(5L);
        assertThat(result.getCouponsRedeemed()).isEqualTo(3L);
        assertThat(result.getTotalOrders()).isEqualTo(2L);

        // product 1: qty 2 + 1 = 3; product 2: qty 3
        assertThat(result.getProductsSold()).hasSize(2);

        result.getProductsSold().forEach(ps -> {
            if (ps.getProductId() == 1) {
                assertThat(ps.getProductName()).isEqualTo("Widget");
                assertThat(ps.getQuantitySold()).isEqualTo(3L);
            } else {
                assertThat(ps.getProductName()).isEqualTo("Gadget");
                assertThat(ps.getQuantitySold()).isEqualTo(3L);
            }
        });
    }

    @Test
    void getAdminSummary_withNoOrders_returnsZeroes() {
        when(orderRepository.findAll()).thenReturn(Collections.emptyList());
        when(orderItemRepository.findAll()).thenReturn(Collections.emptyList());
        when(productRepository.findByIdIn(Collections.emptyList())).thenReturn(Collections.emptyList());
        when(discountCouponRepository.countByIsUsed(false)).thenReturn(0L);
        when(discountCouponRepository.countByIsUsed(true)).thenReturn(0L);

        AdminSummaryResponse result = adminService.getAdminSummary();

        assertThat(result.getGrossRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getNetRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalDiscounts()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalOrders()).isZero();
        assertThat(result.getCouponsGenerated()).isZero();
        assertThat(result.getProductsSold()).isEmpty();
    }
}
