package com.ecommerce.project.service;

import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.payload.CouponDTO;

public interface CouponService {

    CouponDTO createCoupon(CouponDTO couponDTO);

    CouponDTO updateCoupon(Long couponId, CouponDTO couponDTO);

    void deleteCoupon(Long couponId);

    CouponDTO validateCoupon(String code, Double orderAmount);

    CouponDTO applyCoupon(String code);

    java.util.List<CouponDTO> getAllCoupons();

    void validateCouponState(Coupon coupon, String code);
}
