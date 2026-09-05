package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.payload.CouponDTO;
import com.ecommerce.project.repository.CouponRepository;
import com.ecommerce.project.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public CouponDTO createCoupon(CouponDTO dto) {
        if (couponRepository.existsByCode(dto.getCode())) {
            throw new APIException("Coupon code already exists: " + dto.getCode());
        }
        if (dto.getExpiryDate().isBefore(LocalDate.now())) {
            throw new APIException("Expiry date cannot be in the past");
        }

        Coupon coupon = Coupon.builder()
                .code(dto.getCode().toUpperCase())
                .discountPercent(dto.getDiscountPercent())
                .expiryDate(dto.getExpiryDate())
                .maxUses(dto.getMaxUses())
                .usedCount(0)
                .active(true)
                .build();

        coupon = couponRepository.save(coupon);
        return mapToDTO(coupon);
    }

    @Override
    @Transactional
    public CouponDTO updateCoupon(Long couponId, CouponDTO dto) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "couponId", couponId));

        if (dto.getCode() != null) coupon.setCode(dto.getCode().toUpperCase());
        if (dto.getDiscountPercent() != null) coupon.setDiscountPercent(dto.getDiscountPercent());
        if (dto.getExpiryDate() != null) coupon.setExpiryDate(dto.getExpiryDate());
        if (dto.getMaxUses() != null) coupon.setMaxUses(dto.getMaxUses());
        if (dto.getActive() != null) coupon.setActive(dto.getActive());

        coupon = couponRepository.save(coupon);
        return mapToDTO(coupon);
    }

    @Override
    public void deleteCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", "couponId", couponId));
        couponRepository.delete(coupon);
    }

    @Override
    public CouponDTO validateCoupon(String code, Double orderAmount) {
        Coupon coupon = couponRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new APIException("Invalid coupon code: " + code));

        validateCouponState(coupon, code);

        // The discount for this order amount is computed by the caller, which
        // returns it as its own field. It used to be written over usedCount
        // here, so a coupon redeemed twice reported itself as redeemed however
        // many whole currency units the discount happened to come to.
        return mapToDTO(coupon);
    }

    @Override
    public List<CouponDTO> getAllCoupons() {
        return couponRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    public void validateCouponState(Coupon coupon, String code) {
        if (!coupon.getActive()) {
            throw new APIException("Coupon is not active: " + code);
        }
        if (coupon.getExpiryDate().isBefore(LocalDate.now())) {
            throw new APIException("Coupon has expired: " + code);
        }
        if (coupon.getUsedCount() >= coupon.getMaxUses()) {
            throw new APIException("Coupon usage limit reached: " + code);
        }
    }

    private CouponDTO mapToDTO(Coupon coupon) {
        return modelMapper.map(coupon, CouponDTO.class);
    }
}
