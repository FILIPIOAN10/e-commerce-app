package com.ecommerce.project.controller;

import com.ecommerce.project.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProductIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void publicProductsEndpointReturnsPaginatedResponse() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/public/products?pageNumber=0&pageSize=10", String.class);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"content\""));
    }

    @Test
    void publicProductsEndpointReturns200EvenWithNoProducts() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/public/products", String.class);

        assertEquals(200, response.getStatusCode().value());
    }
}
