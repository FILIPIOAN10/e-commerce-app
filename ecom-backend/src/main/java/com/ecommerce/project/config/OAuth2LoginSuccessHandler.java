package com.ecommerce.project.config;

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.RoleRepository;
import com.ecommerce.project.repository.UserRepository;
import com.ecommerce.project.security.jwt.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.http.ResponseCookie;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    @Value("${frontend.url}")
    private String frontEndUrl;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    public OAuth2LoginSuccessHandler(UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     JwtUtils jwtUtils,
                                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;

        DefaultOAuth2User principal = (DefaultOAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = principal.getAttributes();

        String provider = token.getAuthorizedClientRegistrationId();

        String username;
        String email;
        String providerId;

        if ("github".equals(provider)) {

            username = String.valueOf(attributes.get("login"));
            providerId = String.valueOf(attributes.get("id"));

            Object emailObj = attributes.get("email");

            email = (emailObj != null)
                    ? emailObj.toString()
                    : username + "@github.com";

        } else {

            Object nameObj = attributes.get("name");
            Object givenName = attributes.get("given_name");

            // username fără spații (pentru JWT și DB)
            String rawName = nameObj != null ? nameObj.toString() :
                    givenName != null ? givenName.toString() : "user";
            username = rawName.replaceAll("\\s+", "_");

            email = String.valueOf(attributes.get("email"));
            providerId = String.valueOf(attributes.get("sub"));
        }

        // FIND OR CREATE USER
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setUserName(username);
                    newUser.setEmail(email);
                    newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    newUser.setProvider(provider);
                    newUser.setProviderId(providerId);

                    Role role = roleRepository.findByRoleName(AppRole.ROLE_USER)
                            .orElseThrow();

                    newUser.getRoles().add(role);

                    return userRepository.save(newUser);
                });

        // GENERATE JWT
        String jwt = jwtUtils.generateTokenFromUsername(user.getUserName());

        // SET COOKIE (for cookie-based auth) AND pass token as query param (for localStorage-based auth)
        ResponseCookie jwtCookie = ResponseCookie.from(jwtUtils.getJwtCookieName(), jwt)
                .path("/")
                .maxAge(24 * 60 * 60)
                .httpOnly(true)
                .secure(!"dev".equals(activeProfile))
                .sameSite("Lax")
                .build();

        response.addHeader("Set-Cookie", jwtCookie.toString());
        response.sendRedirect(frontEndUrl + "/oauth2/redirect?token=" + jwt);
    }
}