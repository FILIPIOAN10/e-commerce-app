package com.ecommerce.project.util;

import com.ecommerce.project.config.AppConstants;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationUtil {

    public static final int MAX_PAGE_SIZE = 100;

    private PaginationUtil() {
    }

    public static int getMaxPageSize() {
        return MAX_PAGE_SIZE;
    }

    public static int clampPageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public static Pageable buildPageable(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        if (pageNumber == null || pageNumber < 0) {
            pageNumber = 0;
        }
        pageSize = clampPageSize(pageSize);
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
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "id";
        }
        Sort sort = Sort.by(sortBy);
        if (sortOrder != null && sortOrder.equalsIgnoreCase("desc")) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }
        return sort;
    }

    public static Pageable clampPageable(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }
}
