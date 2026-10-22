package com.ecommerce.project.repository;

import com.ecommerce.project.model.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupportConversationRepository extends JpaRepository<SupportConversation, String> {

    Optional<SupportConversation> findByConversationIdAndUserId(String conversationId, Long userId);

    Optional<SupportConversation> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);

    /** GDPR erasure: the transcripts are deleted in SupportPilot; this drops the shop's index of them. */
    @Modifying
    @Query("DELETE FROM SupportConversation c WHERE c.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
