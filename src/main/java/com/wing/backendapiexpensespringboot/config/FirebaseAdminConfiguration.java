package com.wing.backendapiexpensespringboot.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseAdminConfiguration {

    private final FirebaseConfig firebaseConfig;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials = loadCredentials();

        FirebaseOptions.Builder options = FirebaseOptions.builder()
                .setCredentials(credentials);

        if (StringUtils.hasText(firebaseConfig.getProjectId())) {
            options.setProjectId(firebaseConfig.getProjectId());
        }

        return FirebaseApp.initializeApp(options.build());
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (StringUtils.hasText(firebaseConfig.getServiceAccountPath())) {
            // Security Fix: Validate file path to prevent path traversal attacks
            String sanitizedPath = validateAndSanitizePath(firebaseConfig.getServiceAccountPath());
            File credentialsFile = new File(sanitizedPath);

            // Verify the file is within allowed directories and is a regular file
            if (!credentialsFile.exists() || !credentialsFile.isFile()) {
                throw new IllegalStateException(
                        "Firebase service account file not found at path: " + credentialsFile.getAbsolutePath()
                                + ". Set FIREBASE_SERVICE_ACCOUNT_PATH to a valid JSON file."
                );
            }

            // Additional security check: verify canonical path matches to prevent symlink attacks
            String canonicalPath = credentialsFile.getCanonicalPath();
            if (!canonicalPath.equals(credentialsFile.getAbsolutePath())) {
                throw new IllegalStateException(
                        "Firebase service account path contains symbolic links or relative references. "
                                + "Use absolute paths only."
                );
            }

            try (InputStream is = new FileInputStream(credentialsFile)) {
                return GoogleCredentials.fromStream(is);
            }
        }

        if (StringUtils.hasText(firebaseConfig.getServiceAccountJson())) {
            String raw = firebaseConfig.getServiceAccountJson().trim();
            byte[] jsonBytes = raw.startsWith("{")
                    ? raw.getBytes(StandardCharsets.UTF_8)
                    : Base64.getDecoder().decode(raw);

            try (InputStream is = new ByteArrayInputStream(jsonBytes)) {
                return GoogleCredentials.fromStream(is);
            }
        }

        try {
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Firebase Admin credentials are missing. Configure one of: "
                            + "FIREBASE_SERVICE_ACCOUNT_PATH (JSON file path), "
                            + "FIREBASE_SERVICE_ACCOUNT_JSON (raw/base64 JSON), "
                            + "or GOOGLE_APPLICATION_CREDENTIALS for ADC.",
                    exception
            );
        }
    }

    /**
     * Security Fix: Validate and sanitize file path to prevent path traversal attacks.
     * Blocks common path traversal patterns like ../, ..\, and null bytes.
     */
    private String validateAndSanitizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalStateException("Firebase service account path cannot be empty");
        }

        String trimmedPath = path.trim();

        // Check for null bytes (common in path traversal attacks)
        if (trimmedPath.contains("\0")) {
            throw new IllegalStateException(
                    "Firebase service account path contains invalid null byte character"
            );
        }

        // Check for path traversal patterns
        if (trimmedPath.contains("../") || trimmedPath.contains("..\\")) {
            throw new IllegalStateException(
                    "Firebase service account path contains path traversal sequences (../). "
                            + "Use absolute paths only."
            );
        }

        // Check for encoded path traversal attempts
        String lowerPath = trimmedPath.toLowerCase();
        if (lowerPath.contains("%2e%2e") || lowerPath.contains("%252e")) {
            throw new IllegalStateException(
                    "Firebase service account path contains encoded path traversal sequences"
            );
        }

        // Ensure the path ends with .json extension for additional safety
        if (!trimmedPath.toLowerCase().endsWith(".json")) {
            throw new IllegalStateException(
                    "Firebase service account path must point to a .json file"
            );
        }

        return trimmedPath;
    }
}
