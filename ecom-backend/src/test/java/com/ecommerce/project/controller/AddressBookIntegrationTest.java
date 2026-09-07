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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The address book, end to end through the web layer.
 *
 * <p>Both endpoints used to answer 500. {@code AuthUtil.loggedInUser()} returns a
 * <em>detached</em> {@code User} — the repository call that loaded it ran in its
 * own transaction, which has already committed — and the service then read
 * {@code user.getAddresses()}, a LAZY {@code @OneToMany}. With
 * {@code spring.jpa.open-in-view=false} that throws
 * {@code LazyInitializationException}, so checkout could neither list addresses
 * nor add one.
 *
 * <p>Deliberately NOT {@code @Transactional}: a test-managed transaction would
 * hold a session open for the whole method and the detached-entity bug this
 * guards would never reproduce. For the same reason it cannot be a Mockito test
 * — a mocked {@code User} hands back a plain {@code ArrayList} and passes either
 * way.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("Address book")
class AddressBookIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private static final String STREET = "Lazy Init Guard Street";

    private static final String ADDRESS_BODY = """
            {
              "street": "%s",
              "buildingName": "Block 7",
              "city": "Cluj-Napoca",
              "state": "Cluj",
              "country": "Romania",
              "pincode": "400001"
            }
            """.formatted(STREET);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    @DisplayName("a signed-in user can add an address and read it back")
    void createThenListAddresses() throws Exception {
        mockMvc.perform(post("/api/addresses")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADDRESS_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.street").value(STREET));

        mockMvc.perform(get("/api/users/addresses"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(STREET)));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    @DisplayName("an account with no addresses gets an empty list, not a 500")
    void emptyAddressBookIsNotAnError() throws Exception {
        mockMvc.perform(get("/api/users/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
