package com.ecommerce.project.repository;

import com.ecommerce.project.model.ReviewVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewVoteRepository extends JpaRepository<ReviewVote, ReviewVote.ReviewVoteId> {

    Optional<ReviewVote> findByReviewIdAndUserId(Long reviewId, Long userId);

    /**
     * Atomically adjust one of the two counter columns on the review row.
     * A read-modify-write in the service used to do this in application code
     * and lost votes under concurrent updates; this pushes the arithmetic
     * inside a single SQL statement so the DB serialises it. The counter is
     * clamped at zero as a belt-and-suspenders — with the vote-table CRUD
     * above driving deltas, the count can never legitimately go negative,
     * but a stray manual DELETE against review_votes should not underflow.
     */
    @Modifying
    @Query("UPDATE Review r SET r.helpfulCount = CASE WHEN r.helpfulCount + :delta < 0 THEN 0 ELSE r.helpfulCount + :delta END " +
           "WHERE r.id = :reviewId")
    int adjustHelpfulCount(@Param("reviewId") Long reviewId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE Review r SET r.unhelpfulCount = CASE WHEN r.unhelpfulCount + :delta < 0 THEN 0 ELSE r.unhelpfulCount + :delta END " +
           "WHERE r.id = :reviewId")
    int adjustUnhelpfulCount(@Param("reviewId") Long reviewId, @Param("delta") int delta);
}
