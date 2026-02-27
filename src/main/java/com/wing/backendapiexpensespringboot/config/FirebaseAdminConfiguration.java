package com.wing.backendapiexpensespringboot.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
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
            try (InputStream is = new FileInputStream(firebaseConfig.getServiceAccountPath())) {
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

        return GoogleCredentials.getApplicationDefault();
    }
}
