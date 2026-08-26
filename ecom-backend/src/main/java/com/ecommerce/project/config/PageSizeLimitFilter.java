package com.ecommerce.project.config;

import com.ecommerce.project.util.PaginationUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces a server-side upper bound for the {@code pageSize} request parameter
 * to prevent clients from requesting huge pages as a DoS vector.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PageSizeLimitFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String pageSize = request.getParameter("pageSize");
        if (pageSize != null && !pageSize.isBlank()) {
            try {
                int requested = Integer.parseInt(pageSize);
                int clamped = PaginationUtil.clampPageSize(requested);
                if (clamped != requested) {
                    filterChain.doFilter(new PageSizeLimitedRequestWrapper(request, clamped), response);
                    return;
                }
            } catch (NumberFormatException ignored) {
                // Let the controller / binder reject the invalid value.
            }
        }

        filterChain.doFilter(request, response);
    }

    private static class PageSizeLimitedRequestWrapper extends HttpServletRequestWrapper {

        private final int clampedPageSize;

        PageSizeLimitedRequestWrapper(HttpServletRequest request, int clampedPageSize) {
            super(request);
            this.clampedPageSize = clampedPageSize;
        }

        @Override
        public String getParameter(String name) {
            if ("pageSize".equals(name)) {
                return String.valueOf(clampedPageSize);
            }
            return super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(String name) {
            if ("pageSize".equals(name)) {
                return new String[]{String.valueOf(clampedPageSize)};
            }
            return super.getParameterValues(name);
        }
    }
}
