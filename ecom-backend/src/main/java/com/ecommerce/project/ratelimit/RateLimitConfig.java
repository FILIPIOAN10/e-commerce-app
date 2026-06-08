package com.ecommerce.project.ratelimit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class RateLimitConfig {
    @Bean
    public List<RateLimitRule> rateLimitRules() {
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
                new RateLimitRule("cart-remove-product", "DELETE", "/api/carts/*/product/*", 30, oneMinute, RateLimitKeyType.USER)
        );
    }
}
