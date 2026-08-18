package com.ecommerce.project.util;

import com.ecommerce.project.config.AppConstants;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationUtil {

    private PaginationUtil() {
    }

    public static Pageable buildPageable(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        if (pageNumber == null || pageNumber < 0) {
            pageNumber = 0;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
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

    public static Sort buildSort(String sortBy, String sortOrder) {
        Sort sort = Sort.by(sortBy);
        if (sortOrder != null && sortOrder.equalsIgnoreCase("desc")) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        return sort;
    }
}
