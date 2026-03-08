package com.wing.backendapiexpensespringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "firebase")
public class FirebaseConfig {

    private boolean enabled = true;
    private String projectId;
    /**
     * Optional absolute/relative file path to Firebase service account JSON.
     * Recommended for local development.
     */
    private String serviceAccountPath;
    /**
     * Optional raw JSON (or base64-encoded JSON) service account credential.
     * Recommended for container/CI environments.
     */
    private String serviceAccountJson;
    private String devDefaultUid = "dev-user";
    private String devDefaultEmail = "dev@expense-tracker.local";
    private String devDefaultRole = "USER";
}
