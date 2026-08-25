package com.ecommerce.project.repository;

import com.ecommerce.project.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCode(String code);
    boolean existsByCode(String code);

    /**
     * Atomically consumes one use of a coupon, but only while it is still active,
     * unexpired and under its usage limit.
     *
     * @return 1 when the use was consumed, 0 when the coupon is no longer usable.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount + 1 " +
           "WHERE c.id = :id AND c.active = true AND c.expiryDate >= CURRENT_DATE " +
           "AND c.usedCount < c.maxUses")
    int tryConsume(@Param("id") Long id);
}
