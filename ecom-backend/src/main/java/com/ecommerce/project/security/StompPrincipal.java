package com.ecommerce.project.security;

import java.security.Principal;

/**
 * Identity attached to an authenticated STOMP session.
 * <p>
 * {@link #getName()} returns the user's email — the key Spring's user-destination
 * resolver matches against when {@code convertAndSendToUser(email, ...)} is called,
 * and the same key the rest of the application already uses to address a user
 * (see {@code AuthUtil.loggedInEmail()} and {@code AppNotification.recipientEmail}).
 */
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
