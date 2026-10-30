package org.example.neustackapp.service;

import org.example.neustackapp.dto.AddCartItemRequest;
import org.example.neustackapp.dto.CartResponse;
import org.example.neustackapp.exception.InsufficientInventoryException;
import org.example.neustackapp.exception.InvalidCartOperationException;
import org.example.neustackapp.exception.ResourceNotFoundException;
import org.example.neustackapp.model.*;
import org.example.neustackapp.repository.CartItemRepository;
import org.example.neustackapp.repository.CartRepository;
import org.example.neustackapp.repository.InventoryRepository;
import org.example.neustackapp.repository.ProductRepository;
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
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private CartService cartService;

    private UUID cartId;
    private Cart cart;
    private Product product1;
    private Inventory inventory1;

    @BeforeEach
    void setUp() {
        cartId = UUID.randomUUID();
        cart = Cart.builder()
                .id(cartId)
                .createdAt(LocalDateTime.now())
                .build();

        product1 = Product.builder()
                .id(1)
                .name("Samsung")
                .currentUnitPrice(new BigDecimal("1000.00"))
                .build();

        inventory1 = Inventory.builder()
                .id(1)
                .availableInventory(10)
                .build();
    }

    @Test
    void createCart_withoutInitialItem_createsEmptyCart() {
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart c = invocation.getArgument(0);
            c.setId(cartId);
            return c;
        });
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(Collections.emptyList());

        CartResponse response = cartService.createCart(null);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(cartId);
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalPrice()).isEqualByComparingTo("0.00");
    }

    @Test
    void getCart_withItems_calculatesTotalsCorrectly() {
        CartItem item = CartItem.builder()
                .id(new CartItemId(cartId, 1))
                .quantity(2)
                .build();

        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findAllById(List.of(1))).thenReturn(List.of(product1));

        CartResponse response = cartService.getCart(cartId);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getName()).isEqualTo("Samsung");
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(response.getItems().get(0).getTotalPrice()).isEqualByComparingTo("2000.00");
        assertThat(response.getTotalPrice()).isEqualByComparingTo("2000.00");
    }

    @Test
    void addItemToCart_sufficientStock_succeeds() {
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1)).thenReturn(Optional.of(product1));
        when(cartItemRepository.findByIdCartIdAndIdProductId(cartId, 1)).thenReturn(Optional.empty());
        when(inventoryRepository.findById(1)).thenReturn(Optional.of(inventory1));

        CartItem savedItem = CartItem.builder()
                .id(new CartItemId(cartId, 1))
                .quantity(3)
                .build();
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(List.of(savedItem));
        when(productRepository.findAllById(List.of(1))).thenReturn(List.of(product1));

        CartResponse response = cartService.addItemToCart(cartId, new AddCartItemRequest(1, 3));

        verify(cartItemRepository).save(any(CartItem.class));
        assertThat(response.getTotalPrice()).isEqualByComparingTo("3000.00");
    }

    @Test
    void addItemToCart_insufficientStock_throwsException() {
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(productRepository.findById(1)).thenReturn(Optional.of(product1));
        when(cartItemRepository.findByIdCartIdAndIdProductId(cartId, 1)).thenReturn(Optional.empty());
        when(inventoryRepository.findById(1)).thenReturn(Optional.of(inventory1)); // available = 10

        assertThatThrownBy(() -> cartService.addItemToCart(cartId, new AddCartItemRequest(1, 15)))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("Insufficient inventory");

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void removeItemFromCart_quantityGreaterThanOne_decrementsQuantity() {
        CartItem item = CartItem.builder()
                .id(new CartItemId(cartId, 1))
                .quantity(2)
                .build();

        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartIdAndIdProductId(cartId, 1)).thenReturn(Optional.of(item));

        CartItem decremented = CartItem.builder()
                .id(new CartItemId(cartId, 1))
                .quantity(1)
                .build();
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(List.of(decremented));
        when(productRepository.findAllById(List.of(1))).thenReturn(List.of(product1));

        CartResponse response = cartService.removeItemFromCart(cartId, 1);

        verify(cartItemRepository).save(item);
        verify(cartRepository, never()).delete(any(Cart.class));
        assertThat(item.getQuantity()).isEqualTo(1);
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    void removeItemFromCart_quantityEqualsOne_cartBecomesEmpty_deletesCart() {
        CartItem item = CartItem.builder()
                .id(new CartItemId(cartId, 1))
                .quantity(1)
                .build();

        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartIdAndIdProductId(cartId, 1)).thenReturn(Optional.of(item));
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(List.of(item));

        CartResponse response = cartService.removeItemFromCart(cartId, 1);

        verify(cartItemRepository).delete(item);
        verify(cartRepository).delete(cart);
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalPrice()).isEqualByComparingTo("0.00");
    }

    @Test
    void removeItemFromCart_quantityEqualsOne_remainingItemsExist_doesNotDeleteCart() {
        CartItem item1 = CartItem.builder()
                .id(new CartItemId(cartId, 1))
                .quantity(1)
                .build();
        CartItem item2 = CartItem.builder()
                .id(new CartItemId(cartId, 2))
                .quantity(3)
                .build();

        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartIdAndIdProductId(cartId, 1)).thenReturn(Optional.of(item1));
        when(cartItemRepository.findByIdCartId(cartId)).thenReturn(List.of(item1, item2), List.of(item2));

        Product product2 = Product.builder()
                .id(2)
                .name("OnePlus")
                .currentUnitPrice(new BigDecimal("2000.00"))
                .build();
        when(productRepository.findAllById(List.of(2))).thenReturn(List.of(product2));

        CartResponse response = cartService.removeItemFromCart(cartId, 1);

        verify(cartItemRepository).delete(item1);
        verify(cartRepository, never()).delete(any(Cart.class));
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(2);
        assertThat(response.getTotalPrice()).isEqualByComparingTo("6000.00");
    }

    @Test
    void removeItemFromCart_itemNotFound_throwsResourceNotFoundException() {
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdCartIdAndIdProductId(cartId, 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItemFromCart(cartId, 99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found in cart");
    }

    @Test
    void removeItemFromCart_cartNotFound_throwsResourceNotFoundException() {
        when(cartRepository.findById(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItemFromCart(cartId, 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cart with id " + cartId + " not found");
    }

    @Test
    void addItemToCart_whenCartAlreadyCheckedOut_throwsInvalidCartOperationException() {
        cart.setOrderId(UUID.randomUUID());
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));

        AddCartItemRequest request = new AddCartItemRequest(1, 1);
        assertThatThrownBy(() -> cartService.addItemToCart(cartId, request))
                .isInstanceOf(InvalidCartOperationException.class)
                .hasMessageContaining("Cannot modify a cart that has already been checked out");
    }

    @Test
    void removeItemFromCart_whenCartAlreadyCheckedOut_throwsInvalidCartOperationException() {
        cart.setOrderId(UUID.randomUUID());
        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.removeItemFromCart(cartId, 1))
                .isInstanceOf(InvalidCartOperationException.class)
                .hasMessageContaining("Cannot modify a cart that has already been checked out");
    }
}
