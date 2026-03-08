package com.wing.backendapiexpensespringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

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

    public String getProjectId() {
        return normalize(projectId);
    }

    public String getServiceAccountPath() {
        return normalize(serviceAccountPath);
    }

    public String getServiceAccountJson() {
        return normalize(serviceAccountJson);
    }

    public String getDevDefaultUid() {
        return normalize(devDefaultUid);
    }

    public String getDevDefaultEmail() {
        return normalize(devDefaultEmail);
    }

    public String getDevDefaultRole() {
        return normalize(devDefaultRole);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        String trimmed = value.trim();
        if (trimmed.length() >= 2) {
            boolean wrappedInDoubleQuotes = trimmed.startsWith("\"") && trimmed.endsWith("\"");
            boolean wrappedInSingleQuotes = trimmed.startsWith("'") && trimmed.endsWith("'");
            if (wrappedInDoubleQuotes || wrappedInSingleQuotes) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }

        return trimmed;
    }
}
