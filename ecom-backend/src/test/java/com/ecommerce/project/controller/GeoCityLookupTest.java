package com.ecommerce.project.controller;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.service.geo.CityCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * City lookup for the checkout address form.
 *
 * <p>These cities used to be resolved in the browser from a 7.9 MB dataset that
 * shipped in the checkout chunk. They now come from here, so what these tests
 * really protect is that the bundled index is present, parseable, and complete
 * enough that nobody is tempted to put the npm dataset back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@DisplayName("City lookup")
class GeoCityLookupTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CityCatalog cityCatalog;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("a guest can list a state's cities without signing in")
    void citiesArePublic() throws Exception {
        // Guest checkout reaches the address form with no session at all, so a
        // 401 here would break it for exactly the people least likely to report it.
        mockMvc.perform(get("/api/public/geo/cities").param("country", "RO").param("state", "CJ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@ == 'Cluj-Napoca')]").exists());
    }

    @Test
    @DisplayName("the response is cacheable, so the form asks once a day and not once a visit")
    void responseIsCacheable() throws Exception {
        mockMvc.perform(get("/api/public/geo/cities").param("country", "US").param("state", "CA"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=86400, public"));
    }

    @Test
    @DisplayName("an unknown country/state pair is an empty list, not an error")
    void unknownPairIsEmpty() throws Exception {
        // Plenty of territories have no subdivisions in the dataset. A caller
        // asking about one is not making a mistake, and a 404 would push the form
        // into an error state over a legitimately empty answer.
        mockMvc.perform(get("/api/public/geo/cities").param("country", "ZZ").param("state", "QQ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("a missing parameter is a 400, not a 500")
    void missingParameterIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/public/geo/cities").param("country", "RO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("codes match case-insensitively, so the client's casing is not load-bearing")
    void codesAreCaseInsensitive() {
        List<String> upper = cityCatalog.citiesOf("RO", "CJ");
        List<String> lower = cityCatalog.citiesOf("ro", "cj");

        assertThat(upper).isNotEmpty();
        assertThat(lower).isEqualTo(upper);
    }

    @Test
    @DisplayName("the bundled index covers the world, not just the country it was spot-checked on")
    void indexIsComplete() {
        // A truncated or half-generated file would still pass every test above by
        // answering for one lucky state. These are four states on four continents
        // with city counts far above any plausible partial write.
        assertThat(cityCatalog.citiesOf("US", "CA")).hasSizeGreaterThan(500);
        assertThat(cityCatalog.citiesOf("GB", "ENG")).hasSizeGreaterThan(500);
        assertThat(cityCatalog.citiesOf("IN", "MH")).hasSizeGreaterThan(100);
        assertThat(cityCatalog.citiesOf("BR", "SP")).hasSizeGreaterThan(100);
    }

    @Test
    @DisplayName("blank and null codes are answered, not thrown at")
    void blankCodesAreEmpty() {
        assertThat(cityCatalog.citiesOf(null, "CJ")).isEmpty();
        assertThat(cityCatalog.citiesOf("RO", null)).isEmpty();
        assertThat(cityCatalog.citiesOf("  ", "CJ")).isEmpty();
    }
}
