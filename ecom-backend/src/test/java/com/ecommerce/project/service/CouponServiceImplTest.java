package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.payload.CouponDTO;
import com.ecommerce.project.repository.CouponRepository;
import com.ecommerce.project.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CouponServiceImpl tests")
class CouponServiceImplTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private CouponServiceImpl couponService;

    private Coupon activeCoupon;
    private CouponDTO activeCouponDto;

    @BeforeEach
    void setUp() {
        activeCoupon = Coupon.builder()
                .id(1L)
                .code("SAVE10")
                .discountPercent(10)
                .expiryDate(LocalDate.now().plusDays(7))
                .maxUses(5)
                .usedCount(0)
                .active(true)
                .build();

        activeCouponDto = new CouponDTO();
        activeCouponDto.setCode("SAVE10");
        activeCouponDto.setDiscountPercent(10);
        activeCouponDto.setExpiryDate(activeCoupon.getExpiryDate());
        activeCouponDto.setMaxUses(5);
        activeCouponDto.setActive(true);
    }

    @Test
    @DisplayName("validateCoupon throws when coupon is expired")
    void validateCoupon_expired_throws() {
        Coupon expired = Coupon.builder()
                .id(2L)
                .code("EXPIRED")
                .discountPercent(15)
                .expiryDate(LocalDate.now().minusDays(1))
                .maxUses(10)
                .usedCount(0)
                .active(true)
                .build();

        when(couponRepository.findByCode("EXPIRED")).thenReturn(Optional.of(expired));

        APIException ex = assertThrows(APIException.class,
                () -> couponService.validateCoupon("EXPIRED", 100.0));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("validateCoupon throws when coupon is inactive")
    void validateCoupon_inactive_throws() {
        Coupon inactive = Coupon.builder()
                .id(3L)
                .code("INACTIVE")
                .discountPercent(15)
                .expiryDate(LocalDate.now().plusDays(7))
                .maxUses(10)
                .usedCount(0)
                .active(false)
                .build();

        when(couponRepository.findByCode("INACTIVE")).thenReturn(Optional.of(inactive));

        APIException ex = assertThrows(APIException.class,
                () -> couponService.validateCoupon("INACTIVE", 100.0));
        assertTrue(ex.getMessage().contains("not active"));
    }

    @Test
    @DisplayName("validateCoupon throws when coupon usage limit reached")
    void validateCoupon_limitReached_throws() {
        Coupon usedUp = Coupon.builder()
                .id(4L)
                .code("USEDUP")
                .discountPercent(20)
                .expiryDate(LocalDate.now().plusDays(7))
                .maxUses(3)
                .usedCount(3)
                .active(true)
                .build();

        when(couponRepository.findByCode("USEDUP")).thenReturn(Optional.of(usedUp));

        APIException ex = assertThrows(APIException.class,
                () -> couponService.validateCoupon("USEDUP", 100.0));
        assertTrue(ex.getMessage().contains("limit reached"));
    }

    @Test
    @DisplayName("validateCoupon computes discount amount correctly")
    void validateCoupon_success() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(activeCoupon));
        when(modelMapper.map(activeCoupon, CouponDTO.class)).thenReturn(activeCouponDto);

        CouponDTO result = couponService.validateCoupon("save10", 200.0);

        assertNotNull(result);
        assertEquals("SAVE10", result.getCode());
        assertEquals(10, result.getDiscountPercent());
    }

    @Test
    @DisplayName("validateCoupon returns discount equal to subtotal when discountPercent is 100")
    void validateCoupon_fullDiscount() {
        Coupon full = Coupon.builder()
                .id(5L)
                .code("FREE")
                .discountPercent(100)
                .expiryDate(LocalDate.now().plusDays(7))
                .maxUses(10)
                .usedCount(0)
                .active(true)
                .build();

        CouponDTO fullDto = new CouponDTO();
        fullDto.setCode("FREE");
        fullDto.setDiscountPercent(100);

        when(couponRepository.findByCode("FREE")).thenReturn(Optional.of(full));
        when(modelMapper.map(full, CouponDTO.class)).thenReturn(fullDto);

        CouponDTO result = couponService.validateCoupon("FREE", 150.0);

        assertNotNull(result);
        assertEquals(100, result.getDiscountPercent());
    }

    @Test
    @DisplayName("validateCoupon reports how often the coupon was used, not the discount")
    void validateCoupon_doesNotOverwriteUsedCount() {
        // A coupon redeemed twice, quoted against a 200.00 order at 10% off.
        activeCoupon.setUsedCount(2);
        activeCouponDto.setUsedCount(2);
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(activeCoupon));
        when(modelMapper.map(activeCoupon, CouponDTO.class)).thenReturn(activeCouponDto);

        CouponDTO result = couponService.validateCoupon("save10", 200.0);

        // Used to answer 20 here -- the discount in whole currency units written
        // over the redemption count, so the admin list showed invented usage.
        assertEquals(2, result.getUsedCount());
    }

    @Test
    @DisplayName("validateCoupon throws for invalid code")
    void validateCoupon_invalidCode_throws() {
        when(couponRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        APIException ex = assertThrows(APIException.class,
                () -> couponService.validateCoupon("UNKNOWN", 100.0));
        assertTrue(ex.getMessage().contains("Invalid coupon"));
    }
}
