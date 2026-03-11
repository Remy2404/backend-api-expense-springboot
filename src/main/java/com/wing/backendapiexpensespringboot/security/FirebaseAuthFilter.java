package com.wing.backendapiexpensespringboot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.exception.AppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private final AuthCookieService authCookieService;
    private final FirebaseAuthenticationService firebaseAuthenticationService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = extractToken(request);
        if (!StringUtils.hasText(idToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UserPrincipal user = firebaseAuthenticationService.authenticate(idToken, true).principal();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null,
                    user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (AppException exception) {
            SecurityContextHolder.clearContext();
            if (exception.getStatusCode().is4xxClientError()) {
                log.warn("Firebase cookie authentication failed: {}", exception.getMessage());
                authCookieService.clearAccessToken(response);
                writeUnauthorized(response, exception.getMessage());
                return;
            }

            throw exception;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = resolvePath(request);
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/health".equals(path)
                || "/actuator/health".equals(path)
                || "/api/health".equals(path)
                || "/api/actuator/health".equals(path)
                || "/auth".equals(path)
                || path.startsWith("/auth/")
                || "/api/auth".equals(path)
                || path.startsWith("/api/auth/");
    }

    private String resolvePath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return StringUtils.hasText(servletPath) ? servletPath : request.getRequestURI();
    }

    private void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of("detail", detail)));

    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return authCookieService.readAccessToken(request).orElse(null);
    }
}
