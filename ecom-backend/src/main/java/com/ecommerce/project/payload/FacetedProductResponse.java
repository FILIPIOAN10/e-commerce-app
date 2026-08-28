package com.ecommerce.project.payload;

import java.util.List;

/**
 * A page of filtered products together with the facet counts for the same
 * search. Mirrors {@link ProductResponse}'s pagination fields so a client can
 * treat the two interchangeably, plus {@code facets}.
 */
public record FacetedProductResponse(
        List<ProductDTO> content,
        Integer pageNumber,
        Integer pageSize,
        Long totalElements,
        Integer totalPages,
        boolean lastPage,
        ProductFacets facets) {

    public static FacetedProductResponse of(ProductResponse page, ProductFacets facets) {
        return new FacetedProductResponse(
                page.getContent(),
                page.getPageNumber(),
                page.getPageSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLastPage(),
                facets);
    }
}
