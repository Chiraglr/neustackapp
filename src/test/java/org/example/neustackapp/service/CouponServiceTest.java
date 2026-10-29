package org.example.neustackapp.service;

import org.example.neustackapp.dto.CouponResponse;
import org.example.neustackapp.dto.GenerateCouponRequest;
import org.example.neustackapp.model.DiscountCoupon;
import org.example.neustackapp.repository.DiscountCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    // --- generateCoupon tests ---

    @Test
    void generateCoupon_whenCodeIsNew_savesAndReturnsCouponResponse() {
        GenerateCouponRequest request = GenerateCouponRequest.builder()
                .code("SUMMER20")
                .discountPercentage(20)
                .maxLimit(200)
                .build();

        DiscountCoupon saved = DiscountCoupon.builder()
                .id(10)
                .code("SUMMER20")
                .discountPercentage(20)
                .maxLimit(200)
                .isUsed(false)
                .build();

        when(discountCouponRepository.findByCode("SUMMER20")).thenReturn(Optional.empty());
        when(discountCouponRepository.save(any(DiscountCoupon.class))).thenReturn(saved);

        CouponResponse result = couponService.generateCoupon(request);

        assertThat(result.getId()).isEqualTo(10);
        assertThat(result.getCode()).isEqualTo("SUMMER20");
        assertThat(result.getDiscountPercentage()).isEqualTo(20);
        assertThat(result.getMaxLimit()).isEqualTo(200);
        assertThat(result.getIsUsed()).isFalse();

        verify(discountCouponRepository).findByCode("SUMMER20");
        verify(discountCouponRepository).save(any(DiscountCoupon.class));
    }

    @Test
    void generateCoupon_whenCodeAlreadyExists_throwsDataIntegrityViolationException() {
        GenerateCouponRequest request = GenerateCouponRequest.builder()
                .code("DISC10")
                .discountPercentage(10)
                .maxLimit(100)
                .build();

        when(discountCouponRepository.findByCode("DISC10")).thenReturn(Optional.of(coupon1));

        assertThatThrownBy(() -> couponService.generateCoupon(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("DISC10");

        verify(discountCouponRepository).findByCode("DISC10");
        verify(discountCouponRepository, never()).save(any());
    }
}
