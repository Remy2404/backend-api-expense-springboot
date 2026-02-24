package com.wing.backendapiexpensespringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "firebase")
public class FirebaseConfig {

    private String projectId;
    private String audience;
    private String apiKey;

    public String getEffectiveAudience() {
        return audience != null ? audience : projectId;
    }

    public String getVerificationApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        return projectId;
    }
}
