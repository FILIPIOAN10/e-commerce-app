package com.ecommerce.project.controller;

import com.ecommerce.project.config.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request bodies are rejected at the edge rather than deep in a service.
 *
 * <p>Nine {@code @RequestBody} parameters carried no {@code @Valid}, so the
 * constraints on their DTOs never ran — {@code CouponDTO} already declared
 * {@code @Min(1) @Max(100)} on the discount and none of it was enforced. The
 * DTOs that had no constraints at all were worse: a null {@code addressId}
 * reached {@code addressRepository.findById(null)} and a null campaign
 * {@code startTime} reached {@code LocalDateTime.parse}, both surfacing as 500s
 * on input the client got wrong.
 *
 * <p>Each case below is a 400 that used to be a 500 or a silent accept.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RequestValidationTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("a coupon over 100% off is rejected, not stored")
    void couponDiscountAboveHundredIsRejected() throws Exception {
        // Straight through the pricing pipeline this would take more off the
        // running total than the order is worth.
        mockMvc.perform(post("/api/admin/coupons").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TOOMUCH","discountPercent":500,
                                 "expiryDate":"2099-01-01","maxUses":10}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("a coupon with a blank code is rejected")
    void couponBlankCodeIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/coupons").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","discountPercent":10,
                                 "expiryDate":"2099-01-01","maxUses":10}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    @DisplayName("checkout without an address is a 400, not a 500")
    void checkoutWithoutAddressIsRejected() throws Exception {
        // findById(null) throws InvalidDataAccessApiUsageException, which the
        // handler has no case for, so the client used to get a 500 for its own
        // malformed request.
        mockMvc.perform(post("/api/order/users/payments/COD").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"COD"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("a promo campaign without a start time is a 400, not a 500")
    void promoCampaignWithoutStartTimeIsRejected() throws Exception {
        // LocalDateTime.parse(null, FORMATTER) is an NPE inside the service.
        mockMvc.perform(post("/api/admin/promo-campaigns").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Spring sale","discountPercent":20,
                                 "endTime":"2099-01-01T10:00","productIds":[1]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("a promo campaign over 100% off is rejected")
    void promoCampaignAboveHundredIsRejected() throws Exception {
        // Money.percentage(100 - 150) prices the product below zero.
        mockMvc.perform(post("/api/admin/promo-campaigns").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Spring sale","discountPercent":150,
                                 "startTime":"2026-01-01T10:00","endTime":"2099-01-01T10:00",
                                 "productIds":[1]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("an order status update with a blank status is rejected")
    void blankOrderStatusIsRejected() throws Exception {
        mockMvc.perform(put("/api/admin/orders/1/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    @DisplayName("a cart line with a zero quantity is rejected")
    void cartItemWithZeroQuantityIsRejected() throws Exception {
        mockMvc.perform(post("/api/cart/create").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"productId":1,"quantity":0}]"""))
                .andExpect(status().isBadRequest());
    }
}
