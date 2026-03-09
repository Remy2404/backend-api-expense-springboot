package com.wing.backendapiexpensespringboot.security;

import com.wing.backendapiexpensespringboot.config.FirebaseConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "false")
public class DevAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_ENDPOINT_SUFFIXES = Set.of(
            "/health",
            "/actuator/health",
            "/api/health",
            "/api/actuator/health"
    );
    private static final String DEV_UID_HEADER = "X-Dev-Firebase-Uid";
    private static final String DEV_EMAIL_HEADER = "X-Dev-Email";
    private static final String DEV_ROLE_HEADER = "X-Dev-Role";

    private final FirebaseConfig firebaseConfig;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isPublicEndpoint(request.getRequestURI())
                && !isAuthEndpoint(request.getRequestURI())
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserPrincipal user = UserPrincipal.builder()
                    .firebaseUid(resolveValue(request.getHeader(DEV_UID_HEADER), firebaseConfig.getDevDefaultUid()))
                    .email(resolveValue(request.getHeader(DEV_EMAIL_HEADER), firebaseConfig.getDevDefaultEmail()))
                    .role(resolveValue(request.getHeader(DEV_ROLE_HEADER), firebaseConfig.getDevDefaultRole()))
                    .claims(Map.of("mode", "dev"))
                    .build();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINT_SUFFIXES.stream().anyMatch(path::endsWith);
    }

    private boolean isAuthEndpoint(String path) {
        return path.endsWith("/auth") || path.contains("/auth/");
    }

    private String resolveValue(String headerValue, String fallbackValue) {
        return headerValue == null || headerValue.isBlank() ? fallbackValue : headerValue.trim();
    }
}
