package org.example.neustackapp.service;

import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.CouponResponse;
import org.example.neustackapp.model.DiscountCoupon;
import org.example.neustackapp.repository.DiscountCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final DiscountCouponRepository discountCouponRepository;

    @Transactional(readOnly = true)
    public List<CouponResponse> getActiveCoupons() {
        List<DiscountCoupon> activeCoupons = discountCouponRepository.findByIsUsedFalse();
        return activeCoupons.stream()
                .map(CouponResponse::fromEntity)
                .toList();
    }
}
