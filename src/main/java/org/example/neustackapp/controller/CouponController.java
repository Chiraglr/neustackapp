package org.example.neustackapp.controller;

import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.CouponResponse;
import org.example.neustackapp.service.CouponService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @GetMapping("/coupons")
    public ResponseEntity<List<CouponResponse>> getActiveCoupons() {
        List<CouponResponse> coupons = couponService.getActiveCoupons();
        return ResponseEntity.ok(coupons);
    }
}
