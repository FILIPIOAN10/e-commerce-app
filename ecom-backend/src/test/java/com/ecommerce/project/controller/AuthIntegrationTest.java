package com.ecommerce.project.controller;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.security.request.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void signinWithSeedAdminCredentialsReturnsJwt() throws Exception {
        LoginRequest req = new LoginRequest()
                .setUsername("admin")
                .setPassword("adminPass");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signin", entity, String.class);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void signinWithWrongPasswordReturns401() throws Exception {
        LoginRequest req = new LoginRequest()
                .setUsername("admin")
                .setPassword("wrong");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signin", entity, String.class);

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void signinWithNonExistentUserReturns401() throws Exception {
        LoginRequest req = new LoginRequest()
                .setUsername("nobody")
                .setPassword("whatever");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(req), headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signin", entity, String.class);

        assertEquals(401, response.getStatusCode().value());
    }
}
