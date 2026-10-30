package org.example.neustackapp.service;

import org.example.neustackapp.dto.CheckoutRequest;
import org.example.neustackapp.dto.OrderResponse;
import org.example.neustackapp.exception.InsufficientInventoryException;
import org.example.neustackapp.exception.InvalidCartOperationException;
import org.example.neustackapp.exception.ResourceNotFoundException;
import org.example.neustackapp.model.*;
import org.example.neustackapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private DiscountCouponRepository discountCouponRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderCheckoutService orderCheckoutService;

    private UUID cartId;
    private Cart cart;
    private Product product1;
    private Inventory inventory1;
    private CartItem cartItem1;
    private DiscountCoupon coupon1;

    @BeforeEach
    void setUp() {
        cartId = UUID.randomUUID();
        cart = Cart.builder().id(cartId).createdAt(LocalDateTime.now()).build();

        product1 = Product.builder()
                .id(1)
                .name("Samsung")
                .currentUnitPrice(new BigDecimal("1000.00"))
                .build();

        inventory1 = Inventory.builder()
                .id(1)
                .availableInventory(10)
                .build();

        cartItem1 = CartItem.builder()
                .id(new CartItemId(cartId, 1))
                .quantity(2)
                .build();

        coupon1 = DiscountCoupon.builder()
                .id(101)
                .code("DISC10")
                .discountPercentage(10)
                .maxLimit(500)
                .isUsed(false)
                .build();
    }

    @Test
    void checkout_happyPathWithoutCoupon_createsOrderAndDeductsInventory() {
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(List.of(cartItem1));
        when(productRepository.findAllById(List.of(1))).thenReturn(List.of(product1));
        when(inventoryRepository.findByIdForUpdate(1)).thenReturn(Optional.of(inventory1));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.count()).thenReturn(5L);
        when(discountCouponRepository.count()).thenReturn(0L);

        CheckoutRequest request = new CheckoutRequest(cartId, null);
        OrderResponse response = orderCheckoutService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getGrossAmount()).isEqualByComparingTo("2000.00");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("2000.00");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getName()).isEqualTo("Samsung");

        // Verify inventory deducted: 10 - 2 = 8
        assertThat(inventory1.getAvailableInventory()).isEqualTo(8);
        verify(inventoryRepository).save(inventory1);

        // Verify order saved
        verify(orderRepository).save(any(Order.class));
        verify(orderItemRepository).save(any(OrderItem.class));

        // Verify new reward coupon was generated with 10% and max limit 100
        verify(discountCouponRepository).save(argThat(c ->
                c.getDiscountPercentage() == 10 && c.getMaxLimit() == 100 && !c.getIsUsed() && c.getCode().startsWith("REWARD10-")
        ));

        // Verify cart updated with order_id (not deleted)
        verify(cartRepository).save(cart);
        assertThat(cart.getOrderId()).isEqualTo(response.getOrderId());
        verify(cartRepository, never()).delete(any());
        verify(cartItemRepository, never()).deleteAll(any());
    }

    @Test
    void checkout_whenCartAlreadyHasOrderId_returnsExistingOrderWithoutDuplicateCheckout() {
        UUID existingOrderId = UUID.randomUUID();
        cart.setOrderId(existingOrderId);

        Order existingOrder = Order.builder()
                .orderId(existingOrderId)
                .totalAmount(new BigDecimal("1800.00"))
                .discountCodeApplied(101)
                .createdAt(LocalDateTime.now())
                .build();

        OrderItem item = OrderItem.builder()
                .id(new OrderItemId(existingOrderId, 1))
                .quantity(2)
                .purchasedUnitPrice(new BigDecimal("1000.00"))
                .build();

        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart));
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(existingOrder));
        when(orderItemRepository.findByIdOrderId(existingOrderId)).thenReturn(List.of(item));
        when(productRepository.findAllById(List.of(1))).thenReturn(List.of(product1));

        CheckoutRequest request = new CheckoutRequest(cartId, null);
        OrderResponse response = orderCheckoutService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(existingOrderId);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("1800.00");

        // Verify no new order was created and no inventory was deducted
        verify(inventoryRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).delete(any());
    }

    @Test
    void checkout_withValidCoupon_appliesDiscountCappedAtMaxLimit() {
        // Gross = 2 * 1000 = 2000. Discount 10% = 200. Max limit = 500. So discount = 200. Total = 1800.
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(List.of(cartItem1));
        when(discountCouponRepository.findByIdForUpdate(101)).thenReturn(Optional.of(coupon1));
        when(productRepository.findAllById(List.of(1))).thenReturn(List.of(product1));
        when(inventoryRepository.findByIdForUpdate(1)).thenReturn(Optional.of(inventory1));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.count()).thenReturn(5L);
        when(discountCouponRepository.count()).thenReturn(0L);

        CheckoutRequest request = new CheckoutRequest(cartId, 101);
        OrderResponse response = orderCheckoutService.checkout(request);

        assertThat(response.getGrossAmount()).isEqualByComparingTo("2000.00");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("200.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("1800.00");
        assertThat(response.getDiscountCouponId()).isEqualTo(101);

        // Coupon marked as used
        assertThat(coupon1.getIsUsed()).isTrue();
        verify(discountCouponRepository).save(coupon1);
        verify(discountCouponRepository).save(argThat(c ->
                c.getDiscountPercentage() == 10 && c.getMaxLimit() == 100 && !c.getIsUsed()
        ));
    }

    @Test
    void checkout_withAlreadyUsedCoupon_throwsInvalidCartOperationException() {
        coupon1.setIsUsed(true);
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(List.of(cartItem1));
        when(discountCouponRepository.findByIdForUpdate(101)).thenReturn(Optional.of(coupon1));

        CheckoutRequest request = new CheckoutRequest(cartId, 101);

        assertThatThrownBy(() -> orderCheckoutService.checkout(request))
                .isInstanceOf(InvalidCartOperationException.class)
                .hasMessageContaining("already been used");

        verify(orderRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void checkout_withInsufficientInventory_throwsExceptionAndRollsBack() {
        inventory1.setAvailableInventory(1); // Cart needs 2
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(List.of(cartItem1));
        when(productRepository.findAllById(List.of(1))).thenReturn(List.of(product1));
        when(inventoryRepository.findByIdForUpdate(1)).thenReturn(Optional.of(inventory1));

        CheckoutRequest request = new CheckoutRequest(cartId, null);

        assertThatThrownBy(() -> orderCheckoutService.checkout(request))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("Insufficient inventory");

        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).delete(any());
    }

    @Test
    void checkout_emptyCart_throwsInvalidCartOperationException() {
        when(cartRepository.findByIdForUpdate(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(Collections.emptyList());

        CheckoutRequest request = new CheckoutRequest(cartId, null);

        assertThatThrownBy(() -> orderCheckoutService.checkout(request))
                .isInstanceOf(InvalidCartOperationException.class)
                .hasMessageContaining("Cannot checkout an empty cart");
    }

    @Test
    void getOrder_orderExists_returnsOrderDetails() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .totalAmount(new BigDecimal("1800.00"))
                .discountCodeApplied(101)
                .createdAt(LocalDateTime.now())
                .build();

        OrderItem item = OrderItem.builder()
                .id(new OrderItemId(orderId, 1))
                .quantity(2)
                .purchasedUnitPrice(new BigDecimal("1000.00"))
                .build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByIdOrderId(orderId)).thenReturn(List.of(item));
        when(productRepository.findAllById(List.of(1))).thenReturn(List.of(product1));

        OrderResponse response = orderCheckoutService.getOrder(orderId);

        assertThat(response).isNotNull();
        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getGrossAmount()).isEqualByComparingTo("2000.00");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("200.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("1800.00");
        assertThat(response.getDiscountCouponId()).isEqualTo(101);
    }
}
