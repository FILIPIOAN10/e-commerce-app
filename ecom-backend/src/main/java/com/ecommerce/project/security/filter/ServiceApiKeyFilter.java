package com.ecommerce.project.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates a calling service by a shared key in {@code X-Api-Key}, as
 * {@code ROLE_<role>}. Constant-time comparison, so response timing cannot be
 * used to guess the key one character at a time.
 *
 * <p>Deliberately not a {@code @Component}: Spring Boot registers every Filter
 * bean as a servlet filter for ALL requests. This one belongs to one chain only.
 * A blank configured key authenticates nobody — an unconfigured deployment fails
 * closed.
 */
public class ServiceApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    private final byte[] expected;
    private final String principal;
    private final String role;

    public ServiceApiKeyFilter(String expectedKey, String principal, String role) {
        this.expected = expectedKey == null ? new byte[0] : expectedKey.getBytes(StandardCharsets.UTF_8);
        this.principal = principal;
        this.role = role;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        if (expected.length > 0 && supplied != null
                && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected)) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
