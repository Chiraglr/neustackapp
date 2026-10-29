package org.example.neustackapp.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.neustackapp.dto.CouponResponse;
import org.example.neustackapp.dto.GenerateCouponRequest;
import org.example.neustackapp.service.CouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/admin/coupons")
    public ResponseEntity<CouponResponse> generateCoupon(@Valid @RequestBody GenerateCouponRequest request) {
        CouponResponse created = couponService.generateCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
