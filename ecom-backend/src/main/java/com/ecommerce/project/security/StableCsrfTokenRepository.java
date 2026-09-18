package com.ecommerce.project.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * A cookie CSRF repository that hands back the same token for as long as the
 * browser holds one.
 *
 * <p>Spring rotates the CSRF token whenever a request authenticates, which for
 * session auth happens once, at login. This API authenticates every request
 * from its JWT, so every authenticated response deleted the XSRF-TOKEN cookie
 * and issued a replacement — confirmed against the running app: an anonymous
 * request carrying the cookie produced no Set-Cookie at all, an authenticated
 * one produced two.
 *
 * <p>A single-page app pays for that. It reads the cookie when it builds a
 * request, and by the time that request arrives the server may have issued a
 * newer token to a call that overlapped it, so the header no longer matches the
 * cookie and the request is rejected with 403. Which request loses depends on
 * ordering, which is why it looked intermittent and hit checkout hardest — that
 * page makes several calls at once.
 *
 * <p>The rotation is not something this application can turn off through the
 * DSL: {@code CsrfConfigurer} registers the strategy itself, so supplying a
 * {@code sessionAuthenticationStrategy} of our own adds to it rather than
 * replacing it. So the repository refuses the rotation instead: the delete half
 * is ignored, and generating a token returns the one already in the browser. A
 * request with no cookie still mints one, exactly as before.
 *
 * <p>What rotation protects against is fixation — an attacker planting a known
 * token before the victim authenticates. That threat needs a session for the
 * token to be tied to; here the token is only ever compared against the cookie
 * in the same request, so a planted value proves nothing an attacker did not
 * already know, and SameSite=Lax keeps it from being submitted cross-site.
 */
public final class StableCsrfTokenRepository implements CsrfTokenRepository {

    private final CookieCsrfTokenRepository delegate;

    private StableCsrfTokenRepository(CookieCsrfTokenRepository delegate) {
        this.delegate = delegate;
    }

    /** Readable by JavaScript, because the SPA has to echo it back in a header. */
    public static StableCsrfTokenRepository withHttpOnlyFalse() {
        return new StableCsrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        CsrfToken existing = delegate.loadToken(request);
        return (existing != null) ? existing : delegate.generateToken(request);
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // The delete half of a rotation. Dropping it is the point of this class.
        if (token == null) {
            return;
        }
        // Writing a value the browser already holds is a no-op that still costs
        // a Set-Cookie header, and a header is what the client races against.
        CsrfToken existing = delegate.loadToken(request);
        if (existing != null && existing.getToken().equals(token.getToken())) {
            return;
        }
        delegate.saveToken(token, request, response);
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return delegate.loadToken(request);
    }
}
