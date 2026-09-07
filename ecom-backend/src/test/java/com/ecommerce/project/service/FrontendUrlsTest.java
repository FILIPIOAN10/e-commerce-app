package com.ecommerce.project.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendUrlsTest {

    @Test
    void pageLinksCarryTheDefaultLanguageSegment() {
        FrontendUrls urls = new FrontendUrls("http://localhost:5173", "en");

        assertThat(urls.page("/verify-email?token=abc"))
                .isEqualTo("http://localhost:5173/en/verify-email?token=abc");
        assertThat(urls.page("reset-password")).isEqualTo("http://localhost:5173/en/reset-password");
        assertThat(urls.base()).isEqualTo("http://localhost:5173");
    }

    @Test
    void aTrailingSlashOnTheBaseIsNotDoubled() {
        FrontendUrls urls = new FrontendUrls("https://shop.example.com/", "ro");

        assertThat(urls.page("/orders/9")).isEqualTo("https://shop.example.com/ro/orders/9");
    }
}
