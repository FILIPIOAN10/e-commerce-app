package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.ProductSearchService;
import com.ecommerce.project.service.ProductSemanticSearchService;
import com.ecommerce.project.util.ProductMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductSemanticSearchService productSemanticSearchService;
    private final ProductMapper productMapper;

    @Value("${app.search.semantic.top-k:20}")
    private int semanticTopK;

    public ProductSearchServiceImpl(ProductRepository productRepository,
                                    CategoryRepository categoryRepository,
                                    ProductSemanticSearchService productSemanticSearchService,
                                    ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productSemanticSearchService = productSemanticSearchService;
        this.productMapper = productMapper;
    }

    @Override
    public ProductResponse searchByCategory(Long categoryId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", "categoryId", categoryId));

        Sort sortByAndOrder = buildProductSort(sortBy, sortOrder);

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Product> pageProducts = productRepository.findByCategoryOrderByPriceAsc(category, pageDetails);

        List<Product> products = pageProducts.getContent();

        if (products.isEmpty()) {
            throw new APIException(category.getCategoryName() + " category does not have any products");
        }

        return productMapper.buildProductResponse(pageProducts);
    }

    @Override
    public ProductResponse searchProductByKeyword(String keyword, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = buildProductSort(sortBy, sortOrder);

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Product> pageProducts = productRepository.findByProductNameLikeIgnoreCase('%' + keyword + '%', pageDetails);

        List<Product> products = pageProducts.getContent();

        if (products.isEmpty()) {
            throw new APIException("Products not found with keyword " + keyword);
        }

        return productMapper.buildProductResponse(pageProducts);
    }

    @Override
    @Cacheable(
            value = "productSearch",
            key = " 'search/' + (#query == null ? '' : #query.toLowerCase()) + '/' + #semantic + '/' + #pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder"
    )
    public ProductResponse searchProducts(String query, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, Boolean semantic) {
        boolean hasCommaSeparatedTerms = query != null && query.contains(",");
        List<String> terms = parseSearchTerms(query);
        List<String> classicTerms = hasCommaSeparatedTerms ? terms : buildClassicFallbackTerms(query);
        Sort sortByAndOrder = buildProductSort(sortBy, sortOrder);
        boolean shouldUseSemanticSearch = Boolean.TRUE.equals(semantic)
                && productSemanticSearchService.isEnabled()
                && !terms.isEmpty();

        if (!shouldUseSemanticSearch) {
            Pageable pageDetails = PageRequest.of(
                    pageNumber == null ? 0 : pageNumber,
                    pageSize == null ? 10 : pageSize,
                    sortByAndOrder);
            Page<Product> classicPage = productRepository.findAll(buildClassicSearchSpec(classicTerms), pageDetails);
            return productMapper.buildProductResponse(classicPage);
        }

        int safePageNumber = pageNumber == null ? 0 : Math.max(pageNumber, 0);
        int safePageSize = pageSize == null ? 10 : Math.max(pageSize, 1);
        int semanticLimit = Math.max(semanticTopK, (safePageNumber + 1) * safePageSize);
        List<Product> classicProducts = productRepository.findAll(
                buildClassicSearchSpec(classicTerms), PageRequest.of(0, semanticLimit, sortByAndOrder)).getContent();
        List<Long> semanticProductIds = searchSemanticProductIds(query, terms, hasCommaSeparatedTerms, semanticLimit);
        List<Product> semanticProducts = findProductsByOrderedIds(semanticProductIds);
        List<Product> products = hasCommaSeparatedTerms
                ? mergePrioritizedProducts(classicProducts, semanticProducts)
                : mergePrioritizedProducts(semanticProducts, classicProducts);

        return productMapper.buildProductResponse(products, pageNumber, pageSize);
    }

    @Override
    public List<String> searchAutocomplete(String query) {
        List<Product> products = productRepository.findByProductNameLikeIgnoreCase("%" + query + "%",
                PageRequest.of(0, 10)).getContent();
        return products.stream()
                .map(Product::getProductName)
                .distinct()
                .toList();
    }

    @Override
    public List<ProductDTO> getBestSellers(int limit) {
        List<Product> products = productRepository.findBestSellingProducts(
                PageRequest.of(0, limit));
        return productMapper.mapProductsToDTOs(products);
    }

    @Override
    public List<ProductDTO> getNewArrivals(int limit) {
        List<Product> products = productRepository.findAllByOrderByProductIdDesc(
                PageRequest.of(0, limit));
        return productMapper.mapProductsToDTOs(products);
    }

    @Override
    public List<ProductDTO> getOnSaleProducts(int limit) {
        List<Product> products = productRepository.findOnSaleProducts(
                PageRequest.of(0, limit));
        return productMapper.mapProductsToDTOs(products);
    }

    private List<String> parseSearchTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return Stream.of(query.split(","))
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .toList();
    }

    private Sort buildProductSort(String sortBy, String sortOrder) {
        return sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
    }

    private Specification<Product> buildClassicSearchSpec(List<String> terms) {
        if (terms.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> {
            List<Predicate> termPredicates = new ArrayList<>();

            for (String term : terms) {
                String likeTerm = "%" + term.toLowerCase() + "%";
                termPredicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("productName")), likeTerm),
                                cb.like(cb.lower(root.get("description")), likeTerm),
                                cb.like(cb.lower(root.get("tags")), likeTerm),
                                cb.like(cb.lower(root.get("category").get("categoryName")), likeTerm)
                        ));
            }
            return cb.or(termPredicates.toArray(Predicate[]::new));
        };
    }

    private List<Product> mergePrioritizedProducts(List<Product> primaryProducts, List<Product> secondaryProducts) {
        Map<Long, Product> mergedProducts = new LinkedHashMap<>();
        Stream.concat(primaryProducts.stream(), secondaryProducts.stream())
                .forEach(product -> mergedProducts.putIfAbsent(product.getProductId(), product));
        return new ArrayList<>(mergedProducts.values());
    }

    private List<Product> findProductsByOrderedIds(List<Long> productsIds) {
        if (productsIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> productsById = productRepository.findAllById(productsIds).stream()
                .collect(Collectors.toMap(Product::getProductId, product -> product));
        return productsIds.stream()
                .map(productsById::get)
                .filter(product -> product != null)
                .toList();
    }

    public List<String> buildClassicFallbackTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<String> terms = new ArrayList<>();
        String normalizedQuery = query.trim();
        terms.add(normalizedQuery);

        Arrays.stream(normalizedQuery.split("\\s+"))
                .map(this::normalizeSearchToken)
                .filter(token -> token.length() >= 3)
                .filter(token -> !isSearchStopWord(token))
                .forEach(terms::add);

        return terms.stream().distinct().toList();
    }

    private String normalizeSearchToken(String token) {
        return token == null ? "" : token
                .toLowerCase()
                .replaceAll("^[^\\p{L}\\p{N}] +|[^\\p{L}\\p{N}]+$", "");
    }

    private boolean isSearchStopWord(String token) {
        return List.of(
                "ce", "ca", "cu", "de", "din", "la", "si", "sau", "in",
                "pe", "un", "o", "sa", "sunt", "este", "pentru", "cand",
                "cat", "cum", "care", "the", "and", "for", "with"
        ).contains(token);
    }

    private List<Long> searchSemanticProductIds(String query, List<String> terms, boolean hasCommaSeparatedTerms, int limit) {
        if (hasCommaSeparatedTerms) {
            return terms.stream()
                    .flatMap(term -> productSemanticSearchService.searchProductIds(term, limit).stream())
                    .distinct()
                    .limit(limit)
                    .toList();
        }
        return productSemanticSearchService.searchProductIds(query, limit);
    }
}
