package com.wing.backendapiexpensespringboot.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private static final String WILDCARD_ORIGIN = "*";
    private static final String SAME_SITE_NONE = "None";

    private RateLimit rateLimit = new RateLimit();
    private Cors cors = new Cors();
    private Auth auth = new Auth();

    @PostConstruct
    public void validate() {
        if (isWildcardOrigin(cors.allowedOrigins)) {
            throw new IllegalStateException(
                "CORS allowedOrigins cannot be '*' when using credentials"
            );
        }
        if (isSameSiteNone(auth.sameSite) && !Boolean.TRUE.equals(auth.cookieSecure)) {
            throw new IllegalStateException(
                "SameSite=None requires cookieSecure=true"
            );
        }
    }

    private boolean isWildcardOrigin(String allowedOrigins) {
        return WILDCARD_ORIGIN.equals(normalize(allowedOrigins));
    }

    private boolean isSameSiteNone(String sameSite) {
        return SAME_SITE_NONE.equalsIgnoreCase(normalize(sameSite));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    public static class RateLimit {
        private int maxRequestsPerMinute = 60;
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "";
    }

    @Data
    public static class Auth {
        private String accessCookieName = "access_token";
        private String cookiePath = "/";
        private Boolean cookieSecure = true;
        private String sameSite = "Lax";
        private long sessionMaxAgeSeconds = 432000L;
        private long fallbackMaxAgeSeconds = 3600L;
    }
}