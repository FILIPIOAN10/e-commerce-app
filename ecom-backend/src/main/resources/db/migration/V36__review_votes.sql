-- Per-user "helpful/unhelpful" vote rows for reviews.
--
-- The old markReviewHelpful / markReviewUnhelpful endpoints did a plain
-- read-modify-write on reviews.helpful_count (and unhelpful_count) with no
-- record of who voted. Two consequences the audit flagged:
--   1. Vote stuffing: any authenticated user could loop the endpoint and
--      inflate a review's count without limit.
--   2. Lost updates: two concurrent votes both read the same figure,
--      incremented, and wrote back — one vote silently disappeared.
--
-- One row per (review, user) with a unique constraint makes the DB reject a
-- second vote from the same user; a vote change (helpful → unhelpful) becomes
-- an UPDATE of that row, and the two counter columns on reviews stay in step
-- via atomic UPDATE statements in the service.
--
-- ON DELETE CASCADE on the review FK: removing a review takes its vote trail
-- with it. The user FK does not cascade, because a deleted user's votes are
-- more useful preserved (the review's count reflects real reader signal at
-- the time); GDPR erasure pseudonymises users rather than deletes them.

CREATE TABLE IF NOT EXISTS review_votes (
    review_id  BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    vote_type  VARCHAR(16)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_review_votes PRIMARY KEY (review_id, user_id),
    CONSTRAINT fk_review_votes_review FOREIGN KEY (review_id)
        REFERENCES reviews (review_id) ON DELETE CASCADE,
    CONSTRAINT fk_review_votes_user FOREIGN KEY (user_id)
        REFERENCES users (user_id),
    CONSTRAINT ck_review_votes_type CHECK (vote_type IN ('HELPFUL', 'UNHELPFUL'))
);

-- The PK covers (review_id, user_id) lookups. Reads of "who voted on this
-- review" go through review_id first and benefit from a dedicated index.
CREATE INDEX IF NOT EXISTS idx_review_votes_review ON review_votes (review_id);
CREATE INDEX IF NOT EXISTS idx_review_votes_user   ON review_votes (user_id);
