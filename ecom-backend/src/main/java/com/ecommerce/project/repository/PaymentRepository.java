package com.ecommerce.project.repository;

import com.ecommerce.project.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface PaymentRepository extends JpaRepository<Payment,Long> {

    Optional<Payment> findByPgPaymentId(String pgPaymentId);
}
