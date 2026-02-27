package com.wing.backendapiexpensespringboot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_ENDPOINT_SUFFIXES = Set.of("/health", "/actuator/health");

    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;
    private static final int MAX_REQUESTS_PER_MINUTE = 60;

    private final FirebaseAuth firebaseAuth;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RoleLookupService> roleLookupServiceProvider;

    private static final Map<String, RateLimitWindow> rateLimitMap = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String idToken = authHeader.substring(7).trim();
        if (!checkRateLimit(idToken)) {
            response.setStatus(429);
            writeJson(response, Map.of("detail", "Rate limit exceeded. Please retry in a moment."));
            return;
        }

        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken, true);
            UserPrincipal user = toPrincipal(decodedToken, idToken);

            request.setAttribute("userPrincipal", user);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, idToken, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (FirebaseAuthException e) {
            log.warn("Firebase token verification failed: {}", e.getMessage());
            writeUnauthorized(response, "Invalid or expired Firebase token.");
        } catch (Exception e) {
            log.error("Unexpected authentication failure", e);
            writeUnauthorized(response, "Authentication failed.");
        }
    }

    private UserPrincipal toPrincipal(FirebaseToken decodedToken, String rawToken) {
        String firebaseUid = decodedToken.getUid();
        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new IllegalArgumentException("Token missing user identity");
        }

        Map<String, Object> claims = decodedToken.getClaims();
        AppRole role = resolveRole(firebaseUid, claims);

        return UserPrincipal.builder()
                .firebaseUid(firebaseUid)
                .email(decodedToken.getEmail())
                .role(role.name())
                .token(rawToken)
                .claims(claims)
                .build();
    }

    private AppRole resolveRole(String firebaseUid, Map<String, Object> claims) {
        if (claims != null && claims.containsKey("role")) {
            return AppRole.from(claims.get("role"));
        }

        RoleLookupService lookupService = roleLookupServiceProvider.getIfAvailable();
        if (lookupService != null) {
            return lookupService.findRoleByFirebaseUid(firebaseUid)
                    .map(AppRole::from)
                    .orElse(AppRole.USER);
        }

        return AppRole.USER;
    }

    private void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        writeJson(response, Map.of("detail", detail));
    }

    private void writeJson(HttpServletResponse response, Map<String, String> body) throws IOException {
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINT_SUFFIXES.stream().anyMatch(path::endsWith);
    }

    private boolean checkRateLimit(String key) {
        long now = System.currentTimeMillis();
        RateLimitWindow window = rateLimitMap.compute(key, (k, current) -> {
            if (current == null || now - current.windowStartMillis >= RATE_LIMIT_WINDOW_MS) {
                return new RateLimitWindow(now, new AtomicInteger(1));
            }

            current.counter.incrementAndGet();
            return current;
        });

        return window.counter.get() <= MAX_REQUESTS_PER_MINUTE;
    }

    private record RateLimitWindow(long windowStartMillis, AtomicInteger counter) {
    }
}
