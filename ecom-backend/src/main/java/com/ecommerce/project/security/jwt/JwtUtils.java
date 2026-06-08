package com.ecommerce.project.security.jwt;


import com.ecommerce.project.security.services.UserDetailsImpl;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;


@Component
public class JwtUtils {


    private static Logger logger = LoggerFactory.getLogger(JwtUtils.class);
    // Getting JWT From Header

    @Value("${spring.app.jwtSecret}")
    private String jwtSecret;

    @Value("${spring.app.jwtExpirationMs}")
    private int jwtExpirationMs;

    @Value("${spring.ecom.app.jwtCookieName}")
    private String jwtCookie;

//    public String getJwtFromHeader(HttpServletRequest request) {
//        String bearerToken = request.getHeader("Authorization");
//        logger.info("Authorization Header: {}", bearerToken);
//        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
//            return bearerToken.substring(7).trim(); // Remove Bearer prefix
//        }
//        return null;
//    }

    /**
     * Extracts JWT token from HttpOnly cookie.
     */
    public String getJwtFromCookies(HttpServletRequest request) {
        // Extract jwt token from cookies
        Cookie cookie = WebUtils.getCookie(request, jwtCookie);
        if (cookie != null) {
            return cookie.getValue();
        } else {
            return null;
        }
    }

    public String getJwtFromHeader(HttpServletRequest request) {
        // Extract jwt token from cookies
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    // generate cookies
    /**
     * Generates JWT cookie after successful login.
     */
    public ResponseCookie generateJwtCookie(UserDetailsImpl userPrincipal){
        String jwt = generateTokenFromUsername(userPrincipal.getUsername());
        ResponseCookie cookie = ResponseCookie.from(jwtCookie, jwt)
                .path("/api")
                .maxAge(24 * 60 * 60)
                .httpOnly(false)
                .secure(false)
                .build();
        return cookie;
    }


    public String generateJwtToken(UserDetailsImpl userPrincipal){
     return generateTokenFromUsername(userPrincipal.getUsername());
    }


    // generate clean JWT cookie
    /**
     * Returns a clean cookie used to clear authentication state.
     */
    public ResponseCookie getCleanJwtCookie(){
        ResponseCookie cookie = ResponseCookie.from(jwtCookie, null)
                .path("/api")
                .build();
        return cookie;
    }

    // Generating Token from Username
    /**
     * Generates JWT token using username.
     */
    public String generateTokenFromUsername(String username) {
//        String username = userDetails.getUsername();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date((new Date().getTime() + jwtExpirationMs)))
                .signWith(key())
                .compact();

    }
    // Getting Username from JWT Token
    /**
     * Extracts username from JWT token.
     */
    public String getUserNameFromJWTToken(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) key())
                .build().parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
    // Generate Signing key

    /**
     * Generates signing key based on secret key.
     */
    public Key key(){
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtSecret)
        );
    }
    // Validating JWT Token
    /**
     * Validates JWT token.
     */
    public boolean validateJwtToken(String authToken) {
        try {

            System.out.println("Validate");
            Jwts.parser()
                    .verifyWith((SecretKey) key())
                    .build().parseSignedClaims(authToken);
            return true;

        } catch (MalformedJwtException e) {
                logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
                logger.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
                logger.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
                logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
