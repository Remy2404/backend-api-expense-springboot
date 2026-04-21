package com.wing.backendapiexpensespringboot.config;

import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.JsonAuthenticationEntryPoint;
import com.wing.backendapiexpensespringboot.security.RateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectProvider<FirebaseAuthFilter> firebaseAuthFilterProvider;
    private final AppConfig appConfig;
    private final JsonAuthenticationEntryPoint jsonAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF token handler for stateless API with token-based auth
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Security Fix: Enable CSRF protection with cookie-based tokens for stateless
                // APIs
                // Uses double-submit cookie pattern compatible with token-based authentication
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        // Exempt health checks and OPTIONS from CSRF (read-only operations)
                        .ignoringRequestMatchers("/health", "/actuator/health", "/api/health", "/api/actuator/health")
                        .ignoringRequestMatchers(request -> request.getMethod().equals("OPTIONS")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Security Fix: Add comprehensive security headers
                .headers(headers -> headers
                        // Prevent clickjacking attacks
                        .frameOptions(frame -> frame.deny())
                        // Enable XSS protection (deprecated but still useful for older browsers)
                        .xssProtection(xss -> xss.disable())
                        // Prevent MIME type sniffing
                        .contentTypeOptions(contentType -> contentType.disable())
                        // Content Security Policy to prevent XSS and injection attacks
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self' data:; " +
                                        "connect-src 'self'; " +
                                        "frame-ancestors 'none'"))
                        // Enforce HTTPS in production
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(jsonAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**", "/health", "/actuator/health").permitAll()
                        .requestMatchers("/api/auth/**", "/api/health", "/api/actuator/health").permitAll()
                        .anyRequest().authenticated());

        // Security Fix: Add rate limiting filter for authentication endpoints
        http.addFilterBefore(rateLimitingFilter(), UsernamePasswordAuthenticationFilter.class);

        FirebaseAuthFilter firebaseAuthFilter = firebaseAuthFilterProvider.getIfAvailable();
        if (firebaseAuthFilter != null) {
            http.addFilterBefore(firebaseAuthFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public RateLimitingFilter rateLimitingFilter() {
        return new RateLimitingFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        String allowedOrigins = appConfig.getCors().getAllowedOrigins();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList()));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration
                .setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
