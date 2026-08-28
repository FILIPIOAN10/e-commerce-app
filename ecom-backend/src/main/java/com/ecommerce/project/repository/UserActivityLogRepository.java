package com.ecommerce.project.repository;

import com.ecommerce.project.model.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {
    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * One user's trail. Keyed by username rather than a foreign key — which is
     * why erasure has to run this <em>before</em> the username is replaced.
     */
    java.util.List<UserActivityLog> findByUsernameOrderByCreatedAtDesc(String username);
}
