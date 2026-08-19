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
                // Strict limits for authentication endpoints
                new RateLimitRule("auth-signin", "POST", "/api/auth/signin", 5, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("auth-signup", "POST", "/api/auth/signup", 3, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("auth-strict", "*", "/api/auth/**", 10, oneMinute, RateLimitKeyType.IP),

                // Specific resource limits
                new RateLimitRule("order-payment", "POST", "/api/order/users/payments/*", 3, oneMinute, RateLimitKeyType.USER),
                new RateLimitRule("product-search", "GET", "/api/public/products/keyword/*", 30, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("product-search-query", "GET", "/api/public/products/search", 30, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("product-list", "GET", "/api/public/products", 100, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("category-products", "GET", "/api/public/categories/*/products", 100, oneMinute, RateLimitKeyType.IP),
                new RateLimitRule("cart-add-products", "POST", "/api/carts/products/*/quantity/*", 30, oneMinute, RateLimitKeyType.USER),
                new RateLimitRule("cart-update-quantity", "PUT", "/api/cart/products/*/quantity/*", 30, oneMinute, RateLimitKeyType.USER),
                new RateLimitRule("cart-remove-product", "DELETE", "/api/carts/*/product/*", 30, oneMinute, RateLimitKeyType.USER),

                // Default lax limit for everything else
                new RateLimitRule("default-lax", "*", "/**", 300, oneMinute, RateLimitKeyType.USER)
        );
    }
}
