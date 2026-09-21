package com.ecommerce.project.model;

/**
 * Whether a {@link ReviewVote} counts as "helpful" or "unhelpful". Stored as a
 * string in the DB so a new value can be added without a numeric migration and
 * so query results remain readable in a psql shell.
 */
public enum VoteType {
    HELPFUL,
    UNHELPFUL
}
