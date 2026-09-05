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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF is waived only where the caller cannot yet hold a token.
 *
 * <p>The exemption used to be {@code /api/auth/**}, which swept up
 * {@code POST /signout} and the two device-revocation routes — state-changing
 * actions taken by someone already authenticated, which is precisely what CSRF
 * exists to protect. Signout in particular is reachable by a cross-site form
 * post with no preflight to stop it, so any page could sign a visitor out of
 * this application, or drop every session they had.
 *
 * <p>The pre-authentication routes stay exempt because there is genuinely no
 * token to send: sign-in, sign-up, refresh, the password-reset and
 * email-verification pair, and the unlock request a locked-out user makes.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CsrfProtectionTest {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @ParameterizedTest(name = "{0} {1} requires a CSRF token")
    @CsvSource({
            "POST, /api/auth/signout",
            "DELETE, /api/auth/devices",
            "DELETE, /api/auth/devices/some-token",
    })
    @WithMockUser(username = "user1", roles = "USER")
    void authenticatedStateChangesRequireCsrf(String method, String path) throws Exception {
        mockMvc.perform(request(method, path).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} is reachable without a CSRF token")
    @CsvSource({
            "/api/auth/signin",
            "/api/auth/signup",
            "/api/auth/refresh",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/resend-verification",
            "/api/auth/unlock-request",
    })
    @WithAnonymousUser
    void preAuthenticationRoutesStayExempt(String path) throws Exception {
        // The caller has no session and therefore no token. What comes back —
        // 400 for a body these endpoints reject, 401, whatever — does not
        // matter; 403 would mean CSRF turned them away before they were read.
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError(path + " answered 403: CSRF is no longer waived "
                                + "for a route the caller reaches before holding a token");
                    }
                });
    }

    @Test
    @DisplayName("signout succeeds once the token is presented")
    @WithMockUser(username = "user1", roles = "USER")
    void signoutSucceedsWithCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/signout")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder request(String method, String path) {
        return "DELETE".equals(method) ? delete(path) : post(path);
    }
}
