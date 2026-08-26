package com.ecommerce.project.util;

import com.ecommerce.project.config.AppConstants;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public class PaginationUtil {

    private static final int MAX_PAGE_SIZE = 100;

    private PaginationUtil() {
    }

    public static Pageable buildPageable(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        if (pageNumber == null || pageNumber < 0) {
            pageNumber = 0;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
        Sort sort = buildSort(sortBy, sortOrder);
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    public static Pageable buildPageable(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String defaultSortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = defaultSortBy;
        }
        if (sortOrder == null || sortOrder.isBlank()) {
            sortOrder = AppConstants.SORT_DIR;
        }
        return buildPageable(pageNumber, pageSize, sortBy, sortOrder);
    }

    /**
     * Builds a Pageable whose sort property is validated against a whitelist.
     * Prefer this overload for any endpoint where {@code sortBy} comes from the client.
     */
    public static Pageable buildPageable(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder,
                                         String defaultSortBy, Set<String> allowedSortProperties) {
        String safeSortBy = SortWhitelist.sanitize(sortBy, allowedSortProperties, defaultSortBy);
        if (sortOrder == null || sortOrder.isBlank()) {
            sortOrder = AppConstants.SORT_DIR;
        }
        return buildPageable(pageNumber, pageSize, safeSortBy, sortOrder);
    }

    public static Sort buildSort(String sortBy, String sortOrder) {
        if (sortBy == null || sortBy.isBlank()) {
            return Sort.unsorted();
        }
        Sort sort = Sort.by(sortBy);
        if (sortOrder != null && sortOrder.equalsIgnoreCase("desc")) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        return sort;
    }
}
