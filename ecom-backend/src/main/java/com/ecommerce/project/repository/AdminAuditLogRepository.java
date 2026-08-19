package com.ecommerce.project.repository;

import com.ecommerce.project.model.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findTop100ByOrderByCreatedAtDesc();

    List<AdminAuditLog> findByActionOrderByCreatedAtDesc(String action);

    List<AdminAuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);
}
