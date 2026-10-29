package org.example.neustackapp.controller;

import org.example.neustackapp.dto.CouponResponse;
import org.example.neustackapp.service.CouponService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    @Mock
    private CouponService couponService;

    @InjectMocks
    private CouponController couponController;

    @Test
    void getActiveCoupons_returns200OkWithCoupons() {
        CouponResponse coupon = CouponResponse.builder()
                .id(1)
                .code("DISC10")
                .discountPercentage(10)
                .maxLimit(500)
                .isUsed(false)
                .build();

        when(couponService.getActiveCoupons()).thenReturn(List.of(coupon));

        ResponseEntity<List<CouponResponse>> response = couponController.getActiveCoupons();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody().get(0).getCode()).isEqualTo("DISC10");
        verify(couponService).getActiveCoupons();
    }
}
