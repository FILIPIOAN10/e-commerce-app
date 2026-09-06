package com.ecommerce.project.repository;

import com.ecommerce.project.model.DisputeEvidenceFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeEvidenceFileRepository extends JpaRepository<DisputeEvidenceFile, Long> {

    List<DisputeEvidenceFile> findByDisputeIdOrderByUploadedAtAsc(Long disputeId);
}
