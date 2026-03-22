package com.wing.backendapiexpensespringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Data
@Configuration
@ConfigurationProperties(prefix = "realtime")
public class RealtimeConfig {

    private String relayUrl;
    private String relaySecret;
    private String publicSocketUrl;
    private long tokenTtlSeconds = 3600;

    public String normalizedRelayUrl() {
        return normalizeUrl(relayUrl);
    }

    public String normalizedPublicSocketUrl() {
        return normalizeUrl(publicSocketUrl);
    }

    private String normalizeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        return "http://" + trimmed;
    }
}
