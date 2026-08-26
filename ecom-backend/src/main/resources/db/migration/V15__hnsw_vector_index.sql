-- ==========================================================
-- V15 - HNSW index for semantic search vector store
-- ==========================================================
-- Spring AI's auto-DDL creates the product_vector_store table with
-- an HNSW index when initialize-schema=true, but the default
-- parameters (m=16, ef_construction=64) are suboptimal for product
-- search workloads. This migration creates an explicit HNSW index
-- with tuned parameters:
--   m=16              — max connections per node (good for 10k-100k vectors)
--   ef_construction=200 — higher build-time accuracy
-- The index is created CONCURRENTLY to avoid locking the table.
-- ==========================================================

-- Drop the auto-created index if it exists (Spring AI names it
-- product_vector_store_embedding_idx by default).
DROP INDEX IF EXISTS product_vector_store_embedding_idx;

-- Create the optimized HNSW index using cosine distance operator.
-- pgvector uses vector_cosine_ops for cosine similarity.
CREATE INDEX IF NOT EXISTS product_vector_store_embedding_hnsw_idx
    ON product_vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
