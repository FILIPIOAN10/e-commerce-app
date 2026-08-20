package com.ecommerce.project.controller;

import com.ecommerce.project.payload.CouponDTO;
import com.ecommerce.project.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CouponController extends BaseController {

    private final CouponService couponService;

    @PostMapping("/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CouponDTO> createCoupon(@RequestBody CouponDTO couponDTO) {
        return ok(couponService.createCoupon(couponDTO));
    }

    @PutMapping("/admin/coupons/{couponId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CouponDTO> updateCoupon(@PathVariable Long couponId, @RequestBody CouponDTO couponDTO) {
        return ok(couponService.updateCoupon(couponId, couponDTO));
    }

    @DeleteMapping("/admin/coupons/{couponId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteCoupon(@PathVariable Long couponId) {
        couponService.deleteCoupon(couponId);
        return ok(Map.of("message", "Coupon deleted successfully"));
    }

    @GetMapping("/admin/coupons")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CouponDTO>> getAllCoupons() {
        return ok(couponService.getAllCoupons());
    }

    @PostMapping("/coupons/validate")
    public ResponseEntity<?> validateCoupon(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        Double orderAmount = ((Number) body.get("orderAmount")).doubleValue();
        CouponDTO coupon = couponService.validateCoupon(code, orderAmount);
        double discountAmount = orderAmount * coupon.getDiscountPercent() / 100.0;
        double finalAmount = orderAmount - discountAmount;
        return ok(Map.of(
                "coupon", coupon,
                "discountAmount", Math.round(discountAmount * 100.0) / 100.0,
                "finalAmount", Math.round(finalAmount * 100.0) / 100.0
        ));
    }
}
