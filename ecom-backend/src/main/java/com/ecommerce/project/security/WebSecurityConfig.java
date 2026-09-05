package com.ecommerce.project.security;



import com.ecommerce.project.config.OAuth2LoginSuccessHandler;
import com.ecommerce.project.ratelimit.RateLimitFilter;
import com.ecommerce.project.security.jwt.AuthEntryPointJwt;
import com.ecommerce.project.security.jwt.AuthTokenFilter;
import com.ecommerce.project.security.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.ecommerce.project.security.filter.CsrfCookieFilter;

import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;
import org.springframework.context.annotation.Lazy;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    private final AuthEntryPointJwt unauthorizedHandler;

    @Value("${frontend.url}")
    String frontEndUrl;

    public WebSecurityConfig(UserDetailsServiceImpl userDetailsService, AuthEntryPointJwt unauthorizedHandler) {
        this.userDetailsService = userDetailsService;
        this.unauthorizedHandler = unauthorizedHandler;
    }


    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        return authenticationProvider;
    }



    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Actuator, ahead of the main chain.
     *
     * <p>Prometheus scrapes {@code /actuator/prometheus} over the internal
     * network and carries no JWT, so the blanket ADMIN rule meant every metric
     * was blackholed and no alert rule ever evaluated. Opening the endpoint was
     * not the answer — {@code IdorAuthorizationTest} asserts that everything
     * bar health and info stays closed — so the scraper gets a credential of
     * its own instead: HTTP Basic against a single in-memory account that
     * exists only if a password is configured.
     *
     * <p>The JWT filter runs here too, so an ADMIN reaching actuator through
     * the app's own cookie keeps working.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(
            HttpSecurity http,
            AuthTokenFilter authTokenFilter,
            @Value("${app.metrics.scrape.username:prometheus}") String scrapeUser,
            @Value("${app.metrics.scrape.password:}") String scrapePassword) throws Exception {

        http.securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().hasAnyRole("ADMIN", "METRICS"))
                .httpBasic(Customizer.withDefaults())
                .authenticationManager(metricsAuthenticationManager(scrapeUser, scrapePassword))
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Authenticates the metrics scraper and nothing else.
     *
     * <p>Deliberately not wired to {@code UserDetailsServiceImpl}: a scrape
     * credential is not a person, should not appear in the users table, and
     * must not be able to sign in to the application. With no password
     * configured the manager holds no accounts at all, so Basic can never
     * succeed — an unconfigured deployment fails closed rather than open.
     */
    private AuthenticationManager metricsAuthenticationManager(String username, String password) {
        InMemoryUserDetailsManager accounts = new InMemoryUserDetailsManager();
        if (password != null && !password.isBlank()) {
            accounts.createUser(org.springframework.security.core.userdetails.User
                    .withUsername(username)
                    .password(passwordEncoder().encode(password))
                    .roles("METRICS")
                    .build());
        }
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthTokenFilter authTokenFilter,
                                           RateLimitFilter rateLimitFilter,
                                           @Lazy OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler =
                new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

    http.cors(corsConfig -> corsConfig.configurationSource(request -> {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(
                    List.of(frontEndUrl)
            );
            config.setAllowedMethods(
                    List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
            );
            config.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization", "X-XSRF-TOKEN", "X-Requested-With", "Idempotency-Key"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);

            return config;
        }))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        // Exempt the endpoints a caller reaches *before* they hold
                        // a session, since there is no token for them to send yet.
                        // /api/auth/** as a whole also covered signout and device
                        // revocation, which are state-changing actions by someone
                        // already authenticated — exactly what CSRF protects. A
                        // cross-site form POST could sign a user out, or drop
                        // every one of their sessions.
                        .ignoringRequestMatchers(
                                "/api/auth/signin",
                                "/api/auth/signup",
                                "/api/auth/refresh",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/unlock-request",
                                "/oauth2/**", "/login/oauth2/**", "/ws-notifications/**"))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers("/api/auth/**","/error").permitAll()
                        .requestMatchers("/ws-notifications/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/seller/**").hasAnyRole("ADMIN","SELLER")
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/*/questions").permitAll()
                        // Actuator is handled by actuatorFilterChain, which is
                        // ordered ahead of this one. These stay as a backstop in
                        // case that chain's matcher is ever narrowed.
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/images/avatars/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated()).oauth2Login(oauth2 -> oauth2
                    .successHandler(oAuth2LoginSuccessHandler)
                        .failureUrl(frontEndUrl + "/login?error=oauth2")
            );
        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(rateLimitFilter,AuthTokenFilter.class);
        http.addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class);
        http.headers(headers -> headers.frameOptions(
                HeadersConfigurer.FrameOptionsConfig::sameOrigin
        ));
        return http.build();
    }


    // use to completely bypass web security
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web -> web.ignoring()
                .requestMatchers("/v2/api-docs",
                        "/configuration/ui",
                        "/swagger-resources/**",
                        "/configuration/security",
                        "/swagger-ui.html",
                        "/webjars/**"));
    }


}
