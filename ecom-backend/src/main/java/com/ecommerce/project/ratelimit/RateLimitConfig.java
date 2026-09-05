package com.ecommerce.project.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Configuration
public class RateLimitConfig {

    @Value("${rate.limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Bean
    public List<RateLimitRule> rateLimitRules() {
        if (!rateLimitEnabled) {
            return Collections.emptyList();
        }

        Duration oneMinute = Duration.ofMinutes(1);

        return List.of(
                new RateLimitRule("auth-signin", "POST", "/api/auth/signin", 5, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("auth-signup", "POST", "/api/auth/signup", 3, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("order-payment", "POST", "/api/order/users/payments/*", 3, oneMinute, RateLimitKeyType.USER),
                new RateLimitRule("product-search", "GET", "/api/public/products/keyword/*", 30, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("product-search-query", "GET", "/api/public/products/search", 30, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("product-list", "GET", "/api/public/products", 100, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("category-products", "GET", "/api/public/categories/*/products", 100, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("cart-add-products", "POST", "/api/carts/products/*/quantity/*", 30, oneMinute, RateLimitKeyType.USER),
                new RateLimitRule("cart-update-quantity", "PUT", "/api/cart/products/*/quantity/*", 30, oneMinute, RateLimitKeyType.USER),
                new RateLimitRule("cart-remove-product", "DELETE", "/api/carts/*/product/*", 30, oneMinute, RateLimitKeyType.USER),

                // Unauthenticated and each one costs something real when repeated.
                // The three mail endpoints send to an address the caller names, so
                // without a cap they are an email cannon pointed at any inbox and a
                // fast way to burn the SMTP quota and the sender reputation.
                new RateLimitRule("forgot-password", "POST", "/api/auth/forgot-password", 3, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("resend-verification", "POST", "/api/auth/resend-verification", 3, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("contact", "POST", "/api/public/contact", 3, oneMinute, RateLimitKeyType.IP),

                // Guest checkout is permitAll and writes: an address row, an order,
                // stock consumed and coupons redeemed. order-payment covers only the
                // authenticated path, so this was the unmetered way in.
                new RateLimitRule("guest-order", "POST", "/api/public/orders/guest", 5, oneMinute, RateLimitKeyType.IP),

                // Codes are short and guessable, and the endpoint answers whether
                // one exists. Keyed by user because it requires authentication.
                new RateLimitRule("coupon-validate", "POST", "/api/coupons/validate", 10, oneMinute, RateLimitKeyType.USER)
        );
    }
}
