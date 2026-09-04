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
    /**
     * Builds a pageable using an explicit sort whitelist.
     *
     * Every caller must provide an allow-list of valid sort properties.
     */
    public static Pageable buildPageable(
            Integer pageNumber,
            Integer pageSize,
            String sortBy,
            String sortOrder,
            String defaultSortBy,
            Set<String> allowedSortFields) {

        String safeSortBy = SortWhitelist.sanitize(
                sortBy,
                allowedSortFields,
                defaultSortBy
        );

        return buildPageableUnchecked(
                pageNumber,
                pageSize,
                safeSortBy,
                sortOrder
        );
    }

    /**
     * Internal pageable builder.
     *
     * This method MUST NOT be public.
     * The caller must sanitize sortBy before reaching this method.
     */
    private static Pageable buildPageableUnchecked(
            Integer pageNumber,
            Integer pageSize,
            String sortBy,
            String sortOrder) {

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
    /**
     * A whitelisted {@link Sort} with no page attached, for the rare caller that
     * paginates by hand (semantic search merges two result lists before slicing).
     * Same allow-list, same 400 on a rejected key — only the paging differs.
     */
    public static Sort buildSafeSort(String sortBy, String sortOrder,
                                     String defaultSortBy, Set<String> allowedSortFields) {
        return buildSort(SortWhitelist.sanitize(sortBy, allowedSortFields, defaultSortBy), sortOrder);
    }

    public static int clampPageSize(int requested) {
        return Math.min(Math.max(requested, 1), MAX_PAGE_SIZE);
    }

    public static int getMaxPageSize() {
        return MAX_PAGE_SIZE;
    }

    /**
     * Applies the direction to an already-sanitised property. Package-private on
     * purpose: {@code Sort.by(clientInput)} is the whole vulnerability, so the
     * only way to a Sort from outside this package is through
     * {@link #buildPageable} and its allow-list.
     */
    static Sort buildSort(String sortBy, String sortOrder) {
        Sort sort = Sort.by(sortBy);

        if (sortOrder != null && sortOrder.equalsIgnoreCase("desc")) {
            sort = sort.descending();
        } else {
            sort = sort.ascending();
        }

        return sort;
    }
}
