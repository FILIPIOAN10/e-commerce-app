package com.ecommerce.project.repository;

import com.ecommerce.project.model.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    Optional<Dispute> findByStripeDisputeId(String stripeDisputeId);

    boolean existsByStripeDisputeId(String stripeDisputeId);

    List<Dispute> findAllByOrderByCreatedAtDesc();
}
