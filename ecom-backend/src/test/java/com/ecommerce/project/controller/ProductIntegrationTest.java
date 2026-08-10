package com.ecommerce.project.controller;

import com.ecommerce.project.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProductIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void publicProductsEndpointReturnsPaginatedResponse() throws Exception {
        mockMvc.perform(get("/api/public/products?pageNumber=0&pageSize=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    void publicProductsEndpointReturns200EvenWithNoProducts() throws Exception {
        mockMvc.perform(get("/api/public/products"))
                .andExpect(status().isOk());
    }
}
