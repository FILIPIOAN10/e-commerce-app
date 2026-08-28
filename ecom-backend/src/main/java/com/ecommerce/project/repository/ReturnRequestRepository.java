package com.ecommerce.project.repository;

import com.ecommerce.project.model.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    Page<ReturnRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    Page<ReturnRequest> findByUserEmailOrderByRequestedAtDesc(String email, Pageable pageable);

    Optional<ReturnRequest> findByOrderId(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, String status);

    /** Every return this user has raised — read by the GDPR export. */
    java.util.List<ReturnRequest> findByUserEmailOrderByIdAsc(String email);
}
