package org.example.neustackapp.service;

import org.example.neustackapp.dto.ProductInventoryResponse;
import org.example.neustackapp.dto.ProductInventoryViewRequest;
import org.example.neustackapp.model.Inventory;
import org.example.neustackapp.model.Product;
import org.example.neustackapp.repository.InventoryRepository;
import org.example.neustackapp.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private CatalogService catalogService;

    private Product product1;
    private Product product2;
    private Inventory inventory1;

    @BeforeEach
    void setUp() {
        product1 = Product.builder()
                .id(1)
                .name("Samsung")
                .currentUnitPrice(new BigDecimal("1000.00"))
                .build();

        product2 = Product.builder()
                .id(2)
                .name("OnePlus")
                .currentUnitPrice(new BigDecimal("2000.00"))
                .build();

        inventory1 = Inventory.builder()
                .id(1)
                .availableInventory(10)
                .build();
    }

    @Test
    void getProductInventoryView_withValidIds_returnsProductAndInventoryDetails() {
        ProductInventoryViewRequest request = new ProductInventoryViewRequest(List.of(1, 2));

        when(productRepository.findAllById(List.of(1, 2))).thenReturn(List.of(product1, product2));
        when(inventoryRepository.findAllById(List.of(1, 2))).thenReturn(List.of(inventory1));

        List<ProductInventoryResponse> result = catalogService.getProductInventoryView(request);

        assertThat(result).hasSize(2);

        ProductInventoryResponse res1 = result.get(0);
        assertThat(res1.getId()).isEqualTo(1);
        assertThat(res1.getName()).isEqualTo("Samsung");
        assertThat(res1.getCurrentUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(res1.getAvailableInventory()).isEqualTo(10);

        ProductInventoryResponse res2 = result.get(1);
        assertThat(res2.getId()).isEqualTo(2);
        assertThat(res2.getName()).isEqualTo("OnePlus");
        assertThat(res2.getCurrentUnitPrice()).isEqualByComparingTo("2000.00");
        // Product 2 has no inventory row, defaults to 0
        assertThat(res2.getAvailableInventory()).isEqualTo(0);
    }

    @Test
    void getProductInventoryView_withNullOrEmptyRequest_returnsEmptyList() {
        assertThat(catalogService.getProductInventoryView(null)).isEmpty();
        assertThat(catalogService.getProductInventoryView(new ProductInventoryViewRequest(null))).isEmpty();
        assertThat(catalogService.getProductInventoryView(new ProductInventoryViewRequest(Collections.emptyList()))).isEmpty();

        verifyNoInteractions(productRepository, inventoryRepository);
    }

    @Test
    void getProductInventoryView_withNonExistentIds_omitsThem() {
        ProductInventoryViewRequest request = new ProductInventoryViewRequest(List.of(999));

        when(productRepository.findAllById(List.of(999))).thenReturn(Collections.emptyList());

        List<ProductInventoryResponse> result = catalogService.getProductInventoryView(request);

        assertThat(result).isEmpty();
        verify(inventoryRepository, never()).findAllById(anyCollection());
    }
}
