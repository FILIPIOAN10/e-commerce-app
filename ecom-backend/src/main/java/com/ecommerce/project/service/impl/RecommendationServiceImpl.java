package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repository.OrderItemRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.ProductSemanticSearchService;
import com.ecommerce.project.service.RecommendationService;
import com.ecommerce.project.service.RecentlyViewedService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final ProductSemanticSearchService semanticSearchService;
    private final RecentlyViewedService recentlyViewedService;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final AuthUtil authUtil;
    private final ProductMapper productMapper;

    @Override
    public List<ProductDTO> getRecommendedForUser(int limit) {
        Set<Long> excludeIds = new HashSet<>();
        List<String> searchTexts = new ArrayList<>();

        // 1. Produse vizualizate recent
        List<ProductDTO> recentlyViewed = recentlyViewedService.getRecentlyViewedProducts();
        for (ProductDTO p : recentlyViewed) {
            excludeIds.add(p.getProductId());
            searchTexts.add(p.getProductName() + " " + p.getDescription() + " " + p.getTags());
        }

        // 2. Produse din comenzile userului
        String email = authUtil.loggedInEmail();
        if (email != null) {
            List<Product> orderedProducts = orderItemRepository.findOrderedProductsByEmail(email);
            for (Product p : orderedProducts) {
                excludeIds.add(p.getProductId());
                searchTexts.add(p.getProductName() + " " + p.getDescription() + " " + p.getTags());
            }
        }

        // 3. Dacă nu avem istoric, returnăm produse populare (cele mai vândute)
        if (searchTexts.isEmpty()) {
            return getFallbackRecommendations(limit);
        }

        // 4. Construim query-ul combinând toate textele
        String combinedQuery = String.join(" ", searchTexts);

        // 5. Semantic search în PgVector
        List<Long> recommendedIds = semanticSearchService.searchProductIds(combinedQuery, limit * 2);

        // 6. Excludem produsele deja văzute/cumpărate
        List<Long> filteredIds = recommendedIds.stream()
                .filter(id -> !excludeIds.contains(id))
                .limit(limit)
                .toList();

        if (filteredIds.isEmpty()) {
            return getFallbackRecommendations(limit);
        }

        return loadProductsByIds(filteredIds);
    }

    @Override
    public List<ProductDTO> getSimilarProducts(Long productId, int limit) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return List.of();
        }

        // Construim textul de căutare din produsul curent
        String searchText = product.getProductName() + " " + product.getDescription() + " " + product.getTags();

        // Semantic search
        List<Long> similarIds = semanticSearchService.searchProductIds(searchText, limit + 1);

        // Excludem produsul curent
        List<Long> filteredIds = similarIds.stream()
                .filter(id -> !id.equals(productId))
                .limit(limit)
                .toList();

        if (filteredIds.isEmpty()) {
            // Fallback: produse din aceeași categorie
            return getFallbackByCategory(product, limit);
        }

        return loadProductsByIds(filteredIds);
    }

    @Override
    public List<ProductDTO> getFrequentlyBoughtTogether(Long productId, int limit) {
        if (productId == null || limit <= 0) {
            return List.of();
        }

        List<Long> resultIds = new ArrayList<>();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit);
        List<Object[]> rows = orderItemRepository.findFrequentlyBoughtTogether(productId, pageable);

        for (Object[] row : rows) {
            if (row[0] instanceof Number) {
                resultIds.add(((Number) row[0]).longValue());
            }
        }

        if (resultIds.size() < limit) {
            // Fill the rest with similar products while avoiding duplicates
            List<ProductDTO> similar = getSimilarProducts(productId, limit);
            for (ProductDTO dto : similar) {
                if (dto.getProductId() != null && !resultIds.contains(dto.getProductId())) {
                    resultIds.add(dto.getProductId());
                    if (resultIds.size() >= limit) {
                        break;
                    }
                }
            }
        }

        return loadProductsByIds(resultIds);
    }

    private List<ProductDTO> getFallbackRecommendations(int limit) {
        List<Object[]> topSelling = orderItemRepository.getTop10BestSellingProducts();
        if (topSelling.isEmpty()) {
            // Ultimul fallback: cele mai recente produse
            return productRepository.findAll().stream()
                    .sorted(Comparator.comparing(Product::getProductId).reversed())
                    .limit(limit)
                    .map(this::toDTO)
                    .toList();
        }
        // Extragem numele produselor și le căutăm
        List<String> productNames = topSelling.stream()
                .map(row -> (String) row[0])
                .toList();
        return productRepository.findByProductNameLikeIgnoreCase(
                        "%" + productNames.get(0) + "%",
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private List<ProductDTO> getFallbackByCategory(Product product, int limit) {
        if (product.getCategory() == null) {
            return List.of();
        }
        return productRepository.findByCategoryOrderByPriceAsc(
                        product.getCategory(),
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .filter(p -> !p.getProductId().equals(product.getProductId()))
                .map(this::toDTO)
                .toList();
    }

    private List<ProductDTO> loadProductsByIds(List<Long> ids) {
        List<Product> products = productRepository.findAllById(ids);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));
        return ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(this::toDTO)
                .toList();
    }

    private ProductDTO toDTO(Product product) {
        return productMapper.mapProductToDTO(product);
    }
}
