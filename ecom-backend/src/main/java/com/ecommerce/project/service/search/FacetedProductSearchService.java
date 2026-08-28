package com.ecommerce.project.service.search;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.FacetedProductResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.util.ProductMapper;
import com.ecommerce.project.util.ProductSpecifications;
import com.ecommerce.project.util.SortWhitelist;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filtered product search with the counts that make the filters usable.
 *
 * <p>Kept apart from {@link com.ecommerce.project.service.ProductSearchService},
 * which answers a different question: that one is relevance ranking over free
 * text, optionally semantic, and returns a flat list. This one is structured
 * browsing — narrow by category, price, rating and availability, and be told
 * what each further narrowing would cost. Merging them would mean one method
 * that is bad at both.
 */
@Service
@RequiredArgsConstructor
public class FacetedProductSearchService {

    private final ProductRepository productRepository;
    private final ProductFacetCalculator facetCalculator;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public FacetedProductResponse search(ProductFilter filter, Integer pageNumber, Integer pageSize,
                                         String sortBy, String sortOrder) {
        Pageable pageable = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_PRODUCTS_BY, SortWhitelist.PRODUCT);

        Page<Product> page = productRepository.findAll(ProductSpecifications.forFilter(filter), pageable);

        return FacetedProductResponse.of(
                productMapper.buildProductResponse(page),
                facetCalculator.calculate(filter));
    }
}
