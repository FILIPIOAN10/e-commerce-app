package com.ecommerce.project.repository;

import com.ecommerce.project.model.Refund;
import com.ecommerce.project.model.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByStripeRefundId(String stripeRefundId);

    Optional<Refund> findByReturnId(Long returnId);

    List<Refund> findByPaymentIntentIdAndStatus(String paymentIntentId, RefundStatus status);

    boolean existsByReturnId(Long returnId);
}
