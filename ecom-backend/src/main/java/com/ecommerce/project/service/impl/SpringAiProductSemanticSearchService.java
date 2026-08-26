package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.service.ProductSemanticSearchService;
import lombok.extern.slf4j.Slf4j;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Primary
@Service
@ConditionalOnExpression("'${spring.ai.vectorstore.type:none}' == 'pgvector' and '${app.search.semantic.enabled:false}' == 'true'")
public class SpringAiProductSemanticSearchService implements ProductSemanticSearchService {

    private static final String PRODUCT_ID_METADATA = "productId";
    private static final String DOCUMENT_ID_PREFIX = "product-";

    private final VectorStore vectorStore;
    private final MeterRegistry meterRegistry;

    public SpringAiProductSemanticSearchService(VectorStore vectorStore, MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.meterRegistry = meterRegistry;
    }

    @Value("${app.search.semantic.similarity-threshold:0.60}")
    private double similarityThreshold;

    private Counter searchCounter;
    private Counter searchFailureCounter;
    private Counter indexCounter;
    private Counter deleteCounter;
    private Timer searchTimer;

    @jakarta.annotation.PostConstruct
    void initMetrics() {
        searchCounter = Counter.builder("semantic.search.requests")
                .description("Total semantic search requests")
                .tag("operation", "search")
                .register(meterRegistry);
        searchFailureCounter = Counter.builder("semantic.search.failures")
                .description("Semantic search failures")
                .tag("operation", "search")
                .register(meterRegistry);
        indexCounter = Counter.builder("semantic.search.requests")
                .description("Total semantic search requests")
                .tag("operation", "index")
                .register(meterRegistry);
        deleteCounter = Counter.builder("semantic.search.requests")
                .description("Total semantic search requests")
                .tag("operation", "delete")
                .register(meterRegistry);
        searchTimer = Timer.builder("semantic.search.duration")
                .description("Semantic search latency")
                .register(meterRegistry);
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
        searchCounter.increment();
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(limit)
                    .similarityThreshold(similarityThreshold)
                    .build();
            List<Long> productIds = searchTimer.record(() -> vectorStore.similaritySearch(searchRequest).stream()
                    .map(this::extractProductId)
                    .flatMap(Optional::stream)
                    .distinct()
                    .toList());
            meterRegistry.gauge("semantic.search.results", productIds.size());
            return productIds;
        } catch (RuntimeException exception) {
            searchFailureCounter.increment();
            log.warn("Semantic product search failed", exception);
            return List.of();
        }
    }

    @Override
    public void indexProduct(Product product) {
        if (product == null || product.getProductId() == null) {
            return;
        }
        indexCounter.increment();
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
        deleteCounter.increment();
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
