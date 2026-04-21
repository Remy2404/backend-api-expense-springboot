package com.wing.backendapiexpensespringboot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Security Fix: Rate limiting filter for authentication endpoints to prevent brute force attacks.
 * Implements a sliding window rate limiter with IP-based tracking.
 */
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    // Rate limit: 10 requests per minute per IP for auth endpoints
    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long WINDOW_SIZE_MS = 60_000L; // 1 minute
    private static final long CLEANUP_INTERVAL_MS = 300_000L; // 5 minutes

    // Track request counts per IP address
    private final Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Only apply rate limiting to authentication endpoints
        if (isAuthEndpoint(requestPath)) {
            String clientIp = getClientIp(request);

            // Periodic cleanup of expired entries
            performPeriodicCleanup();

            // Check rate limit
            if (isRateLimitExceeded(clientIp)) {
                log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, requestPath);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Check if the request path is an authentication endpoint that should be rate limited.
     */
    private boolean isAuthEndpoint(String path) {
        return path != null && (
                path.startsWith("/auth/") ||
                path.startsWith("/api/auth/") ||
                path.equals("/auth") ||
                path.equals("/api/auth")
        );
    }

    /**
     * Extract client IP address from request, considering proxy headers.
     */
    private String getClientIp(HttpServletRequest request) {
        // Check common proxy headers first
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            int commaIndex = ip.indexOf(',');
            return commaIndex > 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }

        // Fallback to remote address
        return request.getRemoteAddr();
    }

    /**
     * Check if rate limit is exceeded for the given IP address.
     * Uses sliding window algorithm.
     */
    private boolean isRateLimitExceeded(String clientIp) {
        long currentTime = System.currentTimeMillis();

        RateLimitEntry entry = rateLimitMap.computeIfAbsent(clientIp, k -> new RateLimitEntry());

        synchronized (entry) {
            // Reset counter if window has expired
            if (currentTime - entry.windowStart.get() > WINDOW_SIZE_MS) {
                entry.windowStart.set(currentTime);
                entry.requestCount.set(0);
            }

            // Increment and check
            int currentCount = entry.requestCount.incrementAndGet();
            entry.lastAccessTime.set(currentTime);

            return currentCount > MAX_REQUESTS_PER_MINUTE;
        }
    }

    /**
     * Periodically clean up expired entries to prevent memory leaks.
     */
    private void performPeriodicCleanup() {
        long currentTime = System.currentTimeMillis();
        long lastCleanup = lastCleanupTime.get();

        if (currentTime - lastCleanup > CLEANUP_INTERVAL_MS) {
            if (lastCleanupTime.compareAndSet(lastCleanup, currentTime)) {
                rateLimitMap.entrySet().removeIf(entry -> {
                    long lastAccess = entry.getValue().lastAccessTime.get();
                    return currentTime - lastAccess > WINDOW_SIZE_MS * 2;
                });
                log.debug("Rate limit map cleanup completed. Current size: {}", rateLimitMap.size());
            }
        }
    }

    /**
     * Internal class to track rate limit data per IP.
     */
    private static class RateLimitEntry {
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger requestCount = new AtomicInteger(0);
        private final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());
    }
}
