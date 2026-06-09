package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.service.ProductSemanticSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
@ConditionalOnBean(VectorStore.class)
@ConditionalOnProperty(name = "app.search.semantic.enabled", havingValue = "true")
public class SpringAiProductSemanticSearchService implements ProductSemanticSearchService {

    private static final String PRODUCT_ID_METADATA = "productId";
    private static final String DOCUMENT_ID_PREFIX = "product-";

    private final VectorStore vectorStore;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<Long> searchProductIds(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(limit)
                    .similarityThresholdAll()
                    .build();
            return vectorStore.similaritySearch(searchRequest).stream()
                    .map(this::extractProductId)
                    .flatMap(Optional::stream)
                    .distinct()
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("Semantic product search failed", exception);
            return List.of();
        }
    }

    @Override
    public void indexProduct(Product product) {
        if (product == null || product.getProducts() == null) {
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
