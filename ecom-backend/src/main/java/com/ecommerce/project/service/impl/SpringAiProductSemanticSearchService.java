package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.service.ProductSemanticSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Value("${app.search.semantic.timeout:5s}")
    private Duration searchTimeout;

    @Value("${app.search.semantic.bulkhead.max-concurrent:10}")
    private int maxConcurrent;

    @Value("${app.search.semantic.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${app.search.semantic.circuit-breaker.cooldown:30s}")
    private Duration circuitBreakerCooldown;

    private final ExecutorService searchExecutor = Executors.newCachedThreadPool();
    private Semaphore bulkhead;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Long> circuitOpenedAt = new AtomicReference<>(0L);

    private enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    private CircuitState circuitState() {
        long openedAt = circuitOpenedAt.get();
        if (openedAt == 0L) {
            return CircuitState.CLOSED;
        }
        if (System.currentTimeMillis() - openedAt >= circuitBreakerCooldown.toMillis()) {
            return CircuitState.HALF_OPEN;
        }
        return CircuitState.OPEN;
    }

    private boolean tryAcquireCircuit() {
        CircuitState state = circuitState();
        if (state == CircuitState.OPEN) {
            return false;
        }
        return true;
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        circuitOpenedAt.set(0L);
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            circuitOpenedAt.compareAndSet(0L, System.currentTimeMillis());
            log.warn("Semantic search circuit breaker opened after {} consecutive failures", failures);
        }
    }

    private Semaphore bulkhead() {
        if (bulkhead == null) {
            bulkhead = new Semaphore(maxConcurrent, true);
        }
        return bulkhead;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<Long> searchProductIds(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        if (!tryAcquireCircuit()) {
            log.debug("Semantic search circuit breaker is open; returning empty results");
            return List.of();
        }

        if (!bulkhead().tryAcquire()) {
            log.warn("Semantic search bulkhead is full ({} concurrent calls); returning empty results", maxConcurrent);
            return List.of();
        }

        try {
            CompletableFuture<List<Long>> future = CompletableFuture.supplyAsync(
                    () -> doSearch(query, limit), searchExecutor);

            List<Long> result = future.get(searchTimeout.toMillis(), TimeUnit.MILLISECONDS);
            recordSuccess();
            return result;
        } catch (TimeoutException e) {
            recordFailure();
            log.warn("Semantic search timed out after {}", searchTimeout);
            return List.of();
        } catch (ExecutionException e) {
            recordFailure();
            log.warn("Semantic search failed", e.getCause());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordFailure();
            log.warn("Semantic search interrupted");
            return List.of();
        } finally {
            bulkhead().release();
        }
    }

    private List<Long> doSearch(String query, int limit) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .similarityThreshold(similarityThreshold)
                .build();
        return vectorStore.similaritySearch(searchRequest).stream()
                .map(this::extractProductId)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
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

    private Document toDocument(Product product) {
        return new Document(
                documentId(product.getProductId()),
                productSearchText(product),
                Map.of(PRODUCT_ID_METADATA, product.getProductId(),
                        "productName", nullToEmpty(product.getProductName()),
                        "tags",nullToEmpty(product.getTags()),
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

    private Optional<Long> extractProductId(Document document){
        Object productId = document.getMetadata().get(PRODUCT_ID_METADATA);

        if(productId instanceof Number){
            return Optional.of(((Number) productId).longValue());
        }

        if (productId instanceof String &&  !((String) productId).isBlank()){
            try {
                return Optional.of(Long.parseLong((String) productId));
            }catch (NumberFormatException ignored){
                return Optional.empty();
            }
        }
        return Optional.ofNullable(document.getId())
                .filter(id->id.startsWith(DOCUMENT_ID_PREFIX))
                .map(id->id.substring(DOCUMENT_ID_PREFIX.length()))
                .filter(id-> !id.isBlank())
                .flatMap(this::parseProductId);
    }

    private Optional<Long> parseProductId(String value){
        try {
            return Optional.of(Long.parseLong(value));
        }catch (NumberFormatException exception){
            return Optional.empty();
        }
    }

    private String documentId(Long productId){
        return DOCUMENT_ID_PREFIX +productId;
    }

    private String nullToEmpty(String value){
        return Objects.toString(value ,"");
    }
}
