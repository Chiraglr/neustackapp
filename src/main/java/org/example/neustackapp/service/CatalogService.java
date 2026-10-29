package org.example.neustackapp.service;

import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.ProductInventoryResponse;
import org.example.neustackapp.dto.ProductInventoryViewRequest;
import org.example.neustackapp.model.Inventory;
import org.example.neustackapp.model.Product;
import org.example.neustackapp.repository.InventoryRepository;
import org.example.neustackapp.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<ProductInventoryResponse> getProductInventoryView(ProductInventoryViewRequest request) {
        if (request == null || request.getProductIds() == null || request.getProductIds().isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> productIds = request.getProductIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (productIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Product> products = productRepository.findAllById(productIds);
        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> foundProductIds = products.stream()
                .map(Product::getId)
                .toList();

        Map<Integer, Integer> inventoryMap = inventoryRepository.findAllById(foundProductIds).stream()
                .collect(Collectors.toMap(
                        Inventory::getId,
                        Inventory::getAvailableInventory,
                        (existing, replacement) -> existing
                ));

        return products.stream()
                .map(product -> ProductInventoryResponse.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .currentUnitPrice(product.getCurrentUnitPrice())
                        .availableInventory(inventoryMap.getOrDefault(product.getId(), 0))
                        .build())
                .toList();
    }
}
