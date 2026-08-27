package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.service.ProductSemanticSearchService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${spring.ai.vectorstore.type:none}' == 'pgvector' and '${app.search.semantic.enabled:false}' == 'true'")
public class SpringAiProductSemanticSearchService implements ProductSemanticSearchService {

    private static final String PRODUCT_ID_METADATA = "productId";
    private static final String DOCUMENT_ID_PREFIX = "product-";

    private final VectorStore vectorStore;

    @Value("${app.search.semantic.similarity-threshold:0.60}")
    private double similarityThreshold;

    @Value("${app.search.semantic.search-timeout:2s}")
    private Duration searchTimeout;

    @Value("${app.search.semantic.max-concurrent:3}")
    private int maxConcurrent;

    @Value("${app.search.semantic.failure-threshold:3}")
    private int failureThreshold;

    @Value("${app.search.semantic.circuit-breaker-cooldown:1s}")
    private Duration circuitBreakerCooldown;

    private final ExecutorService searchExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long circuitOpenTimestamp = 0;
    private volatile boolean halfOpen = false;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<Long> searchProductIds(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        if (isCircuitOpen()) {
            log.debug("Semantic search circuit breaker is open; skipping call.");
            return List.of();
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(limit)
                    .similarityThreshold(similarityThreshold)
                    .build();

            List<Document> documents = CompletableFuture.supplyAsync(
                            () -> vectorStore.similaritySearch(searchRequest),
                            searchExecutor)
                    .orTimeout(searchTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .join();

            resetCircuit();
            return documents.stream()
                    .map(this::extractProductId)
                    .flatMap(Optional::stream)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recordFailure();
            log.warn("Semantic product search failed", e);
            return List.of();
        }
    }

    @Override
    public void indexProduct(Product product) {
        if (product == null || product.getProductId() == null) {
            return;
        }
        try {
            String documentId = documentId(product.getProductId());
            vectorStore.delete(List.of(documentId));
            vectorStore.add(List.of(toDocument(product)));
        } catch (RuntimeException exception) {
            log.warn("Failed to index product {} in vector store ", product.getProductId(), exception);
        }
    }

    @Override
    public void deleteProduct(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            vectorStore.delete(List.of(documentId(productId)));
        } catch (RuntimeException exception) {
            log.warn("Failed to delete product {} from vector store", productId, exception);
        }
    }

    @PreDestroy
    public void closeSearchExecutor() {
        searchExecutor.close();
    }

    private boolean isCircuitOpen() {
        if (consecutiveFailures.get() < failureThreshold) {
            return false;
        }
        if (halfOpen) {
            return false;
        }
        long openDuration = Duration.between(Instant.ofEpochMilli(circuitOpenTimestamp), Instant.now()).toMillis();
        if (openDuration < circuitBreakerCooldown.toMillis()) {
            return true;
        }
        halfOpen = true;
        return false;
    }

    private void recordFailure() {
        if (halfOpen) {
            consecutiveFailures.set(failureThreshold);
            halfOpen = false;
        } else {
            consecutiveFailures.incrementAndGet();
        }
        circuitOpenTimestamp = System.currentTimeMillis();
    }

    private void resetCircuit() {
        consecutiveFailures.set(0);
        halfOpen = false;
    }

    private Document toDocument(Product product) {
        return new Document(
                documentId(product.getProductId()),
                productSearchText(product),
                Map.of(PRODUCT_ID_METADATA, product.getProductId(),
                        "productName", nullToEmpty(product.getProductName()),
                        "tags", nullToEmpty(product.getTags()),
                        "categoryName", product.getCategory() == null ? " " : nullToEmpty(product.getCategory().getCategoryName()))
        );
    }

    private String productSearchText(Product product) {
        String categoryName = product.getCategory() == null ? "" : product.getCategory().getCategoryName();
        return String.join("\n",
                "Product:" + nullToEmpty(product.getProductName()),
                "Category:" + nullToEmpty(categoryName),
                "Tags:" + nullToEmpty(product.getTags()),
                "Description:" + nullToEmpty(product.getDescription()));

    }

    private Optional<Long> extractProductId(Document document) {
        Object productId = document.getMetadata().get(PRODUCT_ID_METADATA);

        if (productId instanceof Number) {
            return Optional.of(((Number) productId).longValue());
        }

        if (productId instanceof String && !((String) productId).isBlank()) {
            try {
                return Optional.of(Long.parseLong((String) productId));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(document.getId())
                .filter(id -> id.startsWith(DOCUMENT_ID_PREFIX))
                .map(id -> id.substring(DOCUMENT_ID_PREFIX.length()))
                .filter(id -> !id.isBlank())
                .flatMap(this::parseProductId);
    }

    private Optional<Long> parseProductId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String documentId(Long productId) {
        return DOCUMENT_ID_PREFIX + productId;
    }

    private String nullToEmpty(String value) {
        return Objects.toString(value, "");
    }
}
