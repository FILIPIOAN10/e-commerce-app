package com.ecommerce.project.security.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void generatesAnIdWhenNoneSupplied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenInChain = new String[1];
        FilterChain chain = (req, res) -> seenInChain[0] = MDC.get(RequestCorrelationFilter.MDC_KEY);

        filter.doFilter(request, response, chain);

        assertThat(seenInChain[0]).isNotBlank();
        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo(seenInChain[0]);
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).as("MDC is cleared after the request").isNull();
    }

    @Test
    void reusesAndSanitisesAnInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER, "abc-123_DEF.9  <script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seen = new String[1];
        filter.doFilter(request, response, (req, res) -> seen[0] = MDC.get(RequestCorrelationFilter.MDC_KEY));

        assertThat(seen[0]).isEqualTo("abc-123_DEF.9script");
        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo("abc-123_DEF.9script");
    }

    @Test
    void clearsMdcEvenWhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain boom = (req, res) -> { throw new RuntimeException("downstream failure"); };

        try {
            filter.doFilter(request, response, boom);
        } catch (Exception expected) {
            // fall through
        }

        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }
}
