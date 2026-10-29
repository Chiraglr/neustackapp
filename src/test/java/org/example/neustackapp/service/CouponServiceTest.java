package org.example.neustackapp.service;

import org.example.neustackapp.dto.CouponResponse;
import org.example.neustackapp.model.DiscountCoupon;
import org.example.neustackapp.repository.DiscountCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private DiscountCouponRepository discountCouponRepository;

    @InjectMocks
    private CouponService couponService;

    private DiscountCoupon coupon1;
    private DiscountCoupon coupon2;

    @BeforeEach
    void setUp() {
        coupon1 = DiscountCoupon.builder()
                .id(1)
                .code("DISC10")
                .discountPercentage(10)
                .maxLimit(500)
                .isUsed(false)
                .build();

        coupon2 = DiscountCoupon.builder()
                .id(2)
                .code("DISC20")
                .discountPercentage(20)
                .maxLimit(1000)
                .isUsed(false)
                .build();
    }

    @Test
    void getActiveCoupons_whenCouponsExist_returnsActiveCoupons() {
        when(discountCouponRepository.findByIsUsedFalse()).thenReturn(List.of(coupon1, coupon2));

        List<CouponResponse> result = couponService.getActiveCoupons();

        assertThat(result).hasSize(2);

        CouponResponse res1 = result.get(0);
        assertThat(res1.getId()).isEqualTo(1);
        assertThat(res1.getCode()).isEqualTo("DISC10");
        assertThat(res1.getDiscountPercentage()).isEqualTo(10);
        assertThat(res1.getMaxLimit()).isEqualTo(500);
        assertThat(res1.getIsUsed()).isFalse();

        verify(discountCouponRepository).findByIsUsedFalse();
    }

    @Test
    void getActiveCoupons_whenNoActiveCoupons_returnsEmptyList() {
        when(discountCouponRepository.findByIsUsedFalse()).thenReturn(Collections.emptyList());

        List<CouponResponse> result = couponService.getActiveCoupons();

        assertThat(result).isEmpty();
        verify(discountCouponRepository).findByIsUsedFalse();
    }
}
