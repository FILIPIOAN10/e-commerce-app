package com.ecommerce.project.model;

/**
 * Lifecycle of one {@link GdprExport} archive.
 *
 * <p>{@code PENDING} the moment the request is accepted, {@code READY} once the
 * outbox handler has built the ZIP. {@code EXPIRED} is written by the purge
 * sweep after the archive's TTL — the row is kept as a record that the request
 * was served, without the bytes.
 */
public enum GdprExportStatus {
    PENDING,
    READY,
    EXPIRED,
    FAILED
}
