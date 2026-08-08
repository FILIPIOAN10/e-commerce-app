package com.ecommerce.project.repository;

import com.ecommerce.project.model.AppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<AppNotification, Long> {

    Page<AppNotification> findByRecipientEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    long countByRecipientEmailAndReadFalse(String email);

    @Modifying
    @Query("UPDATE AppNotification n SET n.read = true WHERE n.recipientEmail = :email AND n.read = false")
    int markAllAsRead(@Param("email") String email);
}
