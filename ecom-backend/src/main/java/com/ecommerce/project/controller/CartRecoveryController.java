package com.ecommerce.project.controller;

import com.ecommerce.project.payload.ApiResponse;
import com.ecommerce.project.service.cart.CartRecoveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landing point for the abandoned-cart recovery link. Public (the recipient is
 * not necessarily logged in when they click) and safe — the token is single-use
 * and only records the recovery; it grants no access.
 */
@RestController
@RequestMapping("/api/public/carts")
@RequiredArgsConstructor
public class CartRecoveryController {

    private final CartRecoveryService cartRecoveryService;

    @Tag(name = "Cart")
    @PostMapping("/recover")
    public ResponseEntity<ApiResponse> recover(@RequestParam("token") String token) {
        return cartRecoveryService.recover(token)
                .map(cartId -> ResponseEntity.ok(new ApiResponse("Cart recovered", true)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse("Invalid or expired recovery link", false)));
    }
}
