package com.wing.backendapiexpensespringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {

    private RateLimit rateLimit = new RateLimit();
    private Cors cors = new Cors();
    private Auth auth = new Auth();

    @Data
    public static class RateLimit {
        private int maxRequestsPerMinute = 60;
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "*";
    }

    @Data
    public static class Auth {
        private String accessCookieName;
        private String cookiePath;
        private Boolean cookieSecure;
        private String sameSite;
        private long sessionMaxAgeSeconds = 432000L;
        private long fallbackMaxAgeSeconds = 3600L;
    }
}
