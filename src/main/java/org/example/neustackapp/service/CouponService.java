package org.example.neustackapp.service;

import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.CouponResponse;
import org.example.neustackapp.dto.GenerateCouponRequest;
import org.example.neustackapp.model.DiscountCoupon;
import org.example.neustackapp.repository.DiscountCouponRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Manually generate and persist a new coupon.
     * Throws {@link DataIntegrityViolationException} if the code already exists
     * (handled by {@code GlobalExceptionHandler} as HTTP 409).
     */
    @Transactional
    public CouponResponse generateCoupon(GenerateCouponRequest request) {
        // Check for duplicate code before hitting the DB constraint so we can
        // surface a clear error message rather than a raw constraint violation.
        if (discountCouponRepository.findByCode(request.getCode()).isPresent()) {
            throw new DataIntegrityViolationException(
                    "A coupon with code '" + request.getCode() + "' already exists.");
        }

        DiscountCoupon coupon = DiscountCoupon.builder()
                .code(request.getCode())
                .discountPercentage(request.getDiscountPercentage())
                .maxLimit(request.getMaxLimit())
                .build(); // isUsed defaults to false via @Builder.Default

        DiscountCoupon saved = discountCouponRepository.save(coupon);
        return CouponResponse.fromEntity(saved);
    }
}
