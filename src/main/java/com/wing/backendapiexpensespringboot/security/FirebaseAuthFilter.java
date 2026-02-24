package com.wing.backendapiexpensespringboot.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.FirebaseConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private final FirebaseConfig firebaseConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final Map<String, AtomicInteger> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 60;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip authentication for health endpoint
        if (path.equals("/health") || path.equals("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"detail\": \"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7).trim();

        // Rate limiting
        if (!checkRateLimit(token)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"detail\": \"Rate limit exceeded. Please retry in a moment.\"}");
            return;
        }

        try {
            UserPrincipal user = verifyFirebaseToken(token);
            request.setAttribute("userPrincipal", user);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, token, AuthorityUtils.NO_AUTHORITIES);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("Firebase token verification failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"detail\": \"Invalid or expired Firebase token.\"}");
        }
    }

    private UserPrincipal verifyFirebaseToken(String token) throws Exception {
        try {
            String verificationApiKey = firebaseConfig.getVerificationApiKey();
            if (verificationApiKey == null || verificationApiKey.isBlank()) {
                throw new Exception("Firebase API key is not configured");
            }

            // Firebase ID token verification endpoint
            String url = "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" + verificationApiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestBody = "{\"idToken\": \"" + token + "\"}";
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode users = root.get("users");

            if (users == null || !users.isArray() || users.isEmpty()) {
                throw new Exception("Invalid token - no user found");
            }

            JsonNode userNode = users.get(0);
            String firebaseUid = userNode.get("localId").asText();

            if (firebaseUid == null || firebaseUid.isEmpty()) {
                throw new Exception("Token missing user identity");
            }

            return UserPrincipal.builder()
                    .firebaseUid(firebaseUid)
                    .token(token)
                    .build();

        } catch (Exception e) {
            log.error("Error verifying Firebase token: ", e);
            throw new Exception("Failed to verify token: " + e.getMessage());
        }
    }

    private boolean checkRateLimit(String key) {
        rateLimitMap.compute(key, (k, counter) -> {
            if (counter == null) {
                return new AtomicInteger(1);
            }

            if (counter.get() >= MAX_REQUESTS_PER_MINUTE) {
                return counter;
            }

            counter.incrementAndGet();
            return counter;
        });

        AtomicInteger counter = rateLimitMap.get(key);
        return counter.get() <= MAX_REQUESTS_PER_MINUTE;
    }
}
