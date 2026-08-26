package com.ecommerce.project.service;

import com.ecommerce.project.service.impl.SpringAiProductSemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SpringAiProductSemanticSearchService resilience tests")
class SpringAiProductSemanticSearchServiceTest {

    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private SpringAiProductSemanticSearchService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.60);
        ReflectionTestUtils.setField(service, "searchTimeout", Duration.ofSeconds(2));
        ReflectionTestUtils.setField(service, "maxConcurrent", 3);
        ReflectionTestUtils.setField(service, "failureThreshold", 3);
        ReflectionTestUtils.setField(service, "circuitBreakerCooldown", Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("searchProductIds returns results on success")
    void searchProductIds_success() {
        Document doc = new Document("product-1", "text", Map.of("productId", 1L));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        List<Long> result = service.searchProductIds("headphones", 5);

        assertEquals(List.of(1L), result);
    }

    @Test
    @DisplayName("searchProductIds returns empty on vectorStore exception")
    void searchProductIds_exception_returnsEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("OpenAI is down"));

        List<Long> result = service.searchProductIds("headphones", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("searchProductIds times out and returns empty when vectorStore is slow")
    void searchProductIds_timeout_returnsEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(5000);
                    return List.of();
                });

        List<Long> result = service.searchProductIds("headphones", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("circuit breaker opens after consecutive failures and rejects calls")
    void circuitBreaker_opensAfterConsecutiveFailures() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("OpenAI is down"));

        for (int i = 0; i < 3; i++) {
            assertTrue(service.searchProductIds("test", 5).isEmpty());
        }

        verify(vectorStore, times(3)).similaritySearch(any(SearchRequest.class));

        List<Long> result = service.searchProductIds("test", 5);
        assertTrue(result.isEmpty());

        verify(vectorStore, times(3)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @DisplayName("circuit breaker closes after cooldown and a successful half-open probe")
    void circuitBreaker_closesAfterCooldownAndSuccess() throws InterruptedException {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("OpenAI is down"));

        for (int i = 0; i < 3; i++) {
            service.searchProductIds("test", 5);
        }

        Thread.sleep(1100);

        Document doc = new Document("product-1", "text", Map.of("productId", 1L));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));

        List<Long> result = service.searchProductIds("test", 5);
        assertEquals(List.of(1L), result);
    }

    @Test
    @DisplayName("searchProductIds returns empty for blank query")
    void searchProductIds_blankQuery_returnsEmpty() {
        List<Long> result = service.searchProductIds("  ", 5);
        assertTrue(result.isEmpty());
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("searchProductIds returns empty for zero limit")
    void searchProductIds_zeroLimit_returnsEmpty() {
        List<Long> result = service.searchProductIds("test", 0);
        assertTrue(result.isEmpty());
        verifyNoInteractions(vectorStore);
    }
}
