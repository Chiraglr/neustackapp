package org.example.neustackapp.controller;

import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.ProductInventoryResponse;
import org.example.neustackapp.dto.ProductInventoryViewRequest;
import org.example.neustackapp.service.CatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @PostMapping("/product_inventory_view")
    public ResponseEntity<List<ProductInventoryResponse>> getProductInventoryView(
            @RequestBody(required = false) ProductInventoryViewRequest request) {
        List<ProductInventoryResponse> response = catalogService.getProductInventoryView(request);
        return ResponseEntity.ok(response);
    }
}
