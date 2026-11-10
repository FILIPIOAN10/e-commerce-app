package com.ecommerce.project.security;

import com.ecommerce.project.config.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Stripe webhook through the real security chain.
 * <p>
 * Stripe posts with no cookies and no CSRF token; the signature is what
 * authenticates it. The exemption for this path was once lost in a merge and
 * every webhook got a 403 from the CsrfFilter — the service tests are
 * Mockito-only, so nothing noticed. These requests carry no {@code csrf()}
 * post-processor on purpose: they are what Stripe actually sends.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StripeWebhookCsrfTest {

    private static final String WEBHOOK = "/api/public/webhooks/stripe";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("a cookie-less POST with no CSRF token reaches the signature check")
    void webhookIsNotBlockedByCsrf() throws Exception {
        // A bad signature: 400 from our own check proves the request got past
        // the CsrfFilter, which would have answered 403.
        mockMvc.perform(post(WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=not-a-real-signature")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Invalid Stripe webhook signature")));
    }

    @Test
    @DisplayName("the exemption is exact: a neighbouring public POST still needs a CSRF token")
    void exemptionDoesNotWidenToOtherPublicPosts() throws Exception {
        mockMvc.perform(post("/api/public/webhooks/stripe-but-not-really")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
