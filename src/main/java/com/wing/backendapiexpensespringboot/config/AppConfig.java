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
    private Bootstrap bootstrap = new Bootstrap();
    private DatabaseRetry databaseRetry = new DatabaseRetry();

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
        if (bootstrap.async.corePoolSize < 1 || bootstrap.async.maxPoolSize < bootstrap.async.corePoolSize) {
            throw new IllegalStateException("Bootstrap async pool sizes must be positive and max >= core.");
        }
        if (bootstrap.async.queueCapacity < 0) {
            throw new IllegalStateException("Bootstrap async queue capacity must be >= 0.");
        }
        if (databaseRetry.maxAttempts < 1) {
            throw new IllegalStateException("Database retry maxAttempts must be >= 1.");
        }
        if (databaseRetry.initialBackoffMs < 0 || databaseRetry.maxBackoffMs < databaseRetry.initialBackoffMs) {
            throw new IllegalStateException("Database retry backoff values are invalid.");
        }
        if (databaseRetry.multiplier < 1.0d) {
            throw new IllegalStateException("Database retry multiplier must be >= 1.0.");
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

    @Data
    public static class Bootstrap {
        private boolean defaultCategoriesEnabled = true;
        private Async async = new Async();
    }

    @Data
    public static class Async {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 200;
    }

    @Data
    public static class DatabaseRetry {
        private int maxAttempts = 3;
        private long initialBackoffMs = 150L;
        private long maxBackoffMs = 1000L;
        private double multiplier = 2.0d;
    }
}
