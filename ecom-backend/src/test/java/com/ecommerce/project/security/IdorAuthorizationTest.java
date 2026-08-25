package com.ecommerce.project.security;

import com.ecommerce.project.config.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parameterized IDOR authorization suite.
 * <p>
 * Verifies that:
 * <ul>
 *   <li>anonymous callers cannot reach authenticated endpoints (401/403),</li>
 *   <li>plain users cannot reach admin endpoints (403),</li>
 *   <li>plain users cannot read or mutate resources they do not own (403/404),</li>
 *   <li>actuator health remains public while other actuator endpoints are not,</li>
 *   <li>forged guest payments are rejected, and</li>
 *   <li>non-whitelisted sort values are rejected (400).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IdorAuthorizationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private static final String GUEST_ORDER_BODY = """
            {
              "email": "attacker@example.com",
              "paymentMethod": "Stripe",
              "pgName": "Stripe",
              "pgPaymentId": "pi_forged_does_not_exist",
              "pgStatus": "succeeded",
              "pgResponseMessage": "OK",
              "items": [{"productId": 1, "quantity": 1}],
              "address": {
                "street": "Fake Street",
                "buildingName": "Fake Building",
                "city": "Nowhere",
                "state": "Nowhere",
                "country": "Romania",
                "pincode": "000000"
              }
            }
            """;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private MockHttpServletRequestBuilder http(String method, String path) {
        return switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            case "PATCH" -> patch(path);
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
    }

    @WithAnonymousUser
    @ParameterizedTest(name = "anonymous {0} {1}")
    @CsvSource({
            "GET, /api/orders/track/1",
            "GET, /api/orders/my-orders",
            "GET, /api/carts/users/cart",
            "GET, /api/carts",
            "GET, /api/admin/orders",
            "GET, /api/admin/users",
            "GET, /api/user/products/recently-viewed",
            "GET, /actuator/metrics",
            "GET, /actuator/prometheus",
            "PUT, /api/cart/items/1/save-for-later",
            "PUT, /api/cart/items/1/move-to-cart",
            "DELETE, /api/carts/1/product/1",
    })
    void anonymousAccessIsRejected(String method, String path) throws Exception {
        mockMvc.perform(http(method, path)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @WithAnonymousUser
    @Test
    @DisplayName("/actuator/health and /actuator/info are public")
    void actuatorHealthAndInfoArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @WithMockUser(username = "user1", roles = "USER")
    @ParameterizedTest(name = "user {0} {1}")
    @CsvSource({
            "GET, /api/admin/orders",
            "GET, /api/admin/users",
            "GET, /api/carts",
            "GET, /actuator/metrics",
            "GET, /actuator/prometheus",
    })
    void plainUserCannotReachAdminResources(String method, String path) throws Exception {
        mockMvc.perform(http(method, path)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "user1", roles = "USER")
    @ParameterizedTest(name = "user1 cannot GET order {0}")
    @CsvSource({"1", "2", "3", "4", "5"})
    void cannotTrackAnotherUsersOrder(String orderId) throws Exception {
        mockMvc.perform(get("/api/orders/track/{orderId}", orderId))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "user1", roles = "USER")
    @ParameterizedTest(name = "user1 cannot DELETE product from cart {0}")
    @CsvSource({"1", "2", "3", "4", "5"})
    void cannotDeleteFromAnotherUsersCart(String cartId) throws Exception {
        mockMvc.perform(delete("/api/carts/{cartId}/product/1", cartId))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "user1", roles = "USER")
    @ParameterizedTest(name = "user1 cannot modify cart item {0}")
    @CsvSource({"1", "2", "3", "4", "5"})
    void cannotModifyAnotherUsersCartItem(String cartItemId) throws Exception {
        mockMvc.perform(put("/api/cart/items/{cartItemId}/save-for-later", cartItemId))
                .andExpect(status().is4xxClientError());
    }

    @WithAnonymousUser
    @Test
    @DisplayName("guest checkout rejects a forged Stripe payment id")
    void guestCheckoutRejectsForgedPayment() throws Exception {
        mockMvc.perform(post("/api/public/orders/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GUEST_ORDER_BODY))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "user1", roles = "USER")
    @ParameterizedTest(name = "sortBy={0} is rejected")
    @CsvSource({
            "password",
            "user.password",
            "nonExistentProperty",
    })
    void hostileSortByIsRejected(String sortBy) throws Exception {
        mockMvc.perform(get("/api/public/products")
                        .param("sortBy", sortBy))
                .andExpect(status().isBadRequest());
    }
}
