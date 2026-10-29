package org.example.neustackapp.controller;

import org.example.neustackapp.dto.AdminSummaryResponse;
import org.example.neustackapp.dto.ProductSalesSummary;
import org.example.neustackapp.service.AdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    @Test
    void getAdminSummary_returns200OkWithSummary() {
        AdminSummaryResponse summary = AdminSummaryResponse.builder()
                .productsSold(List.of(
                        ProductSalesSummary.builder().productId(1).productName("Widget").quantitySold(10L).build()))
                .grossRevenue(new BigDecimal("500.00"))
                .totalDiscounts(new BigDecimal("50.00"))
                .netRevenue(new BigDecimal("450.00"))
                .couponsGenerated(8L)
                .couponsAvailable(5L)
                .couponsRedeemed(3L)
                .totalOrders(6L)
                .build();

        when(adminService.getAdminSummary()).thenReturn(summary);

        ResponseEntity<AdminSummaryResponse> response = adminController.getAdminSummary();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalOrders()).isEqualTo(6L);
        assertThat(response.getBody().getGrossRevenue()).isEqualByComparingTo("500.00");
        verify(adminService).getAdminSummary();
    }
}
