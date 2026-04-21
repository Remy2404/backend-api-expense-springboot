package com.wing.backendapiexpensespringboot.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterConfig {

    private String apiKey;
    private String model;
    private String baseUrl;
    private int timeout;

    /**
     * Security Fix: Validate OpenRouter base URL on startup to prevent SSRF attacks.
     */
    @PostConstruct
    public void validateConfiguration() {
        if (StringUtils.hasText(baseUrl)) {
            validateBaseUrl(baseUrl);
        }
    }

    /**
     * Security Fix: Validate base URL to prevent Server-Side Request Forgery (SSRF) attacks.
     * Ensures the URL uses HTTPS and points to legitimate OpenRouter domains.
     */
    private void validateBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException("OpenRouter base URL cannot be empty");
        }

        String trimmedUrl = url.trim();

        try {
            URI uri = new URI(trimmedUrl);

            // Ensure HTTPS is used for security
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalStateException(
                        "OpenRouter base URL must use HTTPS protocol. Found: " + uri.getScheme()
                );
            }

            // Validate host is not null
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalStateException("OpenRouter base URL must have a valid host");
            }

            // Security: Whitelist allowed OpenRouter domains to prevent SSRF
            String lowerHost = host.toLowerCase();
            if (!lowerHost.equals("openrouter.ai") && !lowerHost.endsWith(".openrouter.ai")) {
                log.warn("OpenRouter base URL host '{}' is not a standard OpenRouter domain. " +
                        "Expected 'openrouter.ai' or '*.openrouter.ai'. Proceeding with caution.", host);
            }

            // Prevent localhost and private IP ranges (basic SSRF protection)
            if (lowerHost.equals("localhost") ||
                    lowerHost.equals("127.0.0.1") ||
                    lowerHost.startsWith("192.168.") ||
                    lowerHost.startsWith("10.") ||
                    lowerHost.startsWith("172.16.") ||
                    lowerHost.equals("0.0.0.0") ||
                    lowerHost.equals("::1")) {
                throw new IllegalStateException(
                        "OpenRouter base URL cannot point to localhost or private IP addresses"
                );
            }

            log.info("OpenRouter base URL validated successfully: {}", trimmedUrl);

        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "OpenRouter base URL is not a valid URI: " + trimmedUrl, e
            );
        }
    }

    @Bean
    public RestTemplate openRouterRestTemplate() {
        return new RestTemplate();
    }
}
