package com.ecommerce.project.payload.request;

import jakarta.validation.constraints.Size;

public record AdminNoteRequest(
        @Size(max = 500, message = "Note must be at most 500 characters")
        String adminNote) {

    /** The body is optional on approve/reject, so null collapses to empty. */
    public String noteOrEmpty() {
        return adminNote == null ? "" : adminNote;
    }
}
