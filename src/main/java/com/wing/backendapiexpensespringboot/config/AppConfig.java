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

    @Data
    public static class RateLimit {
        private int maxRequestsPerMinute = 60;
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "*";
    }
}
