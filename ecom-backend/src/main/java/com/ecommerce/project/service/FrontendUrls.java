package com.ecommerce.project.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds links back into the single-page app for emails and notifications.
 *
 * <p>The SPA routes everything under a {@code /:lang} segment
 * ({@code /en/verify-email}, not {@code /verify-email}) — a bare path matches the
 * {@code :lang} route, is rejected as an unknown language, and the redirect drops
 * the token. Every outbound link therefore has to carry a language. Emails are
 * sent in English, so {@code frontend.default-lang} (default {@code en}) is the
 * one used; there is no per-recipient locale yet.
 */
@Component
public class FrontendUrls {

    private final String base;
    private final String lang;

    public FrontendUrls(@Value("${frontend.url}") String frontendUrl,
                        @Value("${frontend.default-lang:en}") String defaultLang) {
        this.base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.lang = defaultLang;
    }

    /**
     * A language-prefixed SPA link. {@code page("/verify-email?token=abc")} →
     * {@code http://localhost:5173/en/verify-email?token=abc}.
     */
    public String page(String path) {
        String clean = path.startsWith("/") ? path : "/" + path;
        return base + "/" + lang + clean;
    }

    /** The configured base, unprefixed — for links that are not SPA routes (e.g. an API download URL). */
    public String base() {
        return base;
    }
}
