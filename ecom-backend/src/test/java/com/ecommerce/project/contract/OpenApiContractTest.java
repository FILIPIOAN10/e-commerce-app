package com.ecommerce.project.contract;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.ecommerce.project.config.TestcontainersConfiguration;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
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

    @BeforeAll
    void setUp() {
        RestAssured.baseURI = "http://localhost:" + port;

        // Fetch the OpenAPI spec from the running app (springdoc generates it at runtime)
        String openApiSpec = given()
                .accept(ContentType.JSON)
                .when()
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        validator = OpenApiInteractionValidator
                .createForInlineApiSpecification(openApiSpec)
                .build();
    }

    @Test
    void publicProductsEndpointMatchesOpenApiSchema() {
        Response response = given()
                .queryParam("pageNumber", "0")
                .queryParam("pageSize", "10")
                .when()
                .get("/api/public/products")
                .then()
                .statusCode(200)
                .extract()
                .response();

        SimpleResponse apiResponse = SimpleResponse.Builder
                .ok()
                .withBody(response.asString())
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
    void signinEndpointMatchesOpenApiSchema() {
        String requestBody = """
                {
                    "username": "admin",
                    "password": "adminPass"
                }
                """;

        Response response = given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/auth/signin")
                .then()
                .extract()
                .response();

        SimpleResponse apiResponse = SimpleResponse.Builder
                .status(response.statusCode())
                .withBody(response.asString())
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
    void signinWithWrongPasswordReturns401MatchingSchema() {
        String requestBody = """
                {
                    "username": "admin",
                    "password": "wrong"
                }
                """;

        Response response = given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/auth/signin")
                .then()
                .statusCode(401)
                .extract()
                .response();

        SimpleResponse apiResponse = SimpleResponse.Builder
                .status(401)
                .withBody(response.asString())
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
    void publicCategoriesEndpointMatchesOpenApiSchema() {
        Response response = given()
                .when()
                .get("/api/public/categories")
                .then()
                .statusCode(200)
                .extract()
                .response();

        SimpleResponse apiResponse = SimpleResponse.Builder
                .ok()
                .withBody(response.asString())
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
