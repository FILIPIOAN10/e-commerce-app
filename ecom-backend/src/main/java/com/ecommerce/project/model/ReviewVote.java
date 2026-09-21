package com.ecommerce.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One row per (review, user) vote. The composite primary key doubles as the
 * uniqueness constraint that keeps the same user from voting twice — the DB is
 * the last line of defence against vote stuffing, so the constraint sits on
 * the table not just in application code.
 */
@Entity
@Table(name = "review_votes")
@IdClass(ReviewVote.ReviewVoteId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVote {

    @Id
    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false, length = 16)
    private VoteType voteType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Composite key. JPA needs a dedicated class for {@code @IdClass}. */
    public static class ReviewVoteId implements Serializable {
        private Long reviewId;
        private Long userId;

        public ReviewVoteId() {}

        public ReviewVoteId(Long reviewId, Long userId) {
            this.reviewId = reviewId;
            this.userId = userId;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ReviewVoteId that)) return false;
            return Objects.equals(reviewId, that.reviewId) && Objects.equals(userId, that.userId);
        }
        @Override public int hashCode() { return Objects.hash(reviewId, userId); }
    }
}
