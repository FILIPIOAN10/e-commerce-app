package com.ecommerce.project.contract;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.ecommerce.project.config.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiContractTest {

    @LocalServerPort
    int port;

    private OpenApiInteractionValidator validator;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @BeforeAll
    void setUp() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertEquals(200, response.statusCode(), "Failed to fetch OpenAPI spec");
        String openApiSpec = response.body();

        validator = OpenApiInteractionValidator
                .createForInlineApiSpecification(openApiSpec)
                .build();
    }

    @Test
    void publicProductsEndpointMatchesOpenApiSchema() throws Exception {
        HttpResponse<String> response = get("/api/public/products?pageNumber=0&pageSize=10");
        assertEquals(200, response.statusCode());

        SimpleResponse apiResponse = SimpleResponse.Builder
                .ok()
                .withBody(response.body())
                .withContentType("application/json")
                .build();

        SimpleRequest apiRequest = SimpleRequest.Builder
                .get("/api/public/products")
                .withQueryParam("pageNumber", "0")
                .withQueryParam("pageSize", "10")
                .build();

        ValidationReport report = validator.validate(apiRequest, apiResponse);
        assertTrue(report.hasErrors() == false,
                "OpenAPI contract validation failed for GET /api/public/products:\n" +
                        report.getMessages().stream()
                                .map(m -> m.getMessage() + " (key: " + m.getKey() + ")")
                                .reduce("", (a, b) -> a + "\n  - " + b));
    }

    @Test
    void signinEndpointMatchesOpenApiSchema() throws Exception {
        String requestBody = """
                {
                    "username": "admin",
                    "password": "adminPass"
                }
                """;

        HttpResponse<String> response = post("/api/auth/signin", requestBody);

        SimpleResponse apiResponse = SimpleResponse.Builder
                .status(response.statusCode())
                .withBody(response.body())
                .withContentType("application/json")
                .build();

        SimpleRequest apiRequest = SimpleRequest.Builder
                .post("/api/auth/signin")
                .withContentType("application/json")
                .withBody(requestBody)
                .build();

        ValidationReport report = validator.validate(apiRequest, apiResponse);
        assertTrue(report.hasErrors() == false,
                "OpenAPI contract validation failed for POST /api/auth/signin:\n" +
                        report.getMessages().stream()
                                .map(m -> m.getMessage() + " (key: " + m.getKey() + ")")
                                .reduce("", (a, b) -> a + "\n  - " + b));
    }

    @Test
    void signinWithWrongPasswordReturns401MatchingSchema() throws Exception {
        String requestBody = """
                {
                    "username": "admin",
                    "password": "wrong"
                }
                """;

        HttpResponse<String> response = post("/api/auth/signin", requestBody);
        assertEquals(401, response.statusCode());

        SimpleResponse apiResponse = SimpleResponse.Builder
                .status(401)
                .withBody(response.body())
                .withContentType("application/json")
                .build();

        SimpleRequest apiRequest = SimpleRequest.Builder
                .post("/api/auth/signin")
                .withContentType("application/json")
                .withBody(requestBody)
                .build();

        ValidationReport report = validator.validate(apiRequest, apiResponse);
        assertTrue(report.hasErrors() == false,
                "OpenAPI contract validation failed for 401 response on POST /api/auth/signin:\n" +
                        report.getMessages().stream()
                                .map(m -> m.getMessage() + " (key: " + m.getKey() + ")")
                                .reduce("", (a, b) -> a + "\n  - " + b));
    }

    @Test
    void publicCategoriesEndpointMatchesOpenApiSchema() throws Exception {
        HttpResponse<String> response = get("/api/public/categories");
        assertEquals(200, response.statusCode());

        SimpleResponse apiResponse = SimpleResponse.Builder
                .ok()
                .withBody(response.body())
                .withContentType("application/json")
                .build();

        SimpleRequest apiRequest = SimpleRequest.Builder
                .get("/api/public/categories")
                .build();

        ValidationReport report = validator.validate(apiRequest, apiResponse);
        assertTrue(report.hasErrors() == false,
                "OpenAPI contract validation failed for GET /api/public/categories:\n" +
                        report.getMessages().stream()
                                .map(m -> m.getMessage() + " (key: " + m.getKey() + ")")
                                .reduce("", (a, b) -> a + "\n  - " + b));
    }
}
