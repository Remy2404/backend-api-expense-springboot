package com.wing.backendapiexpensespringboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class DatabaseEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String PROPERTY_SOURCE_NAME = "databaseEnvironmentOverrides";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        NormalizedDatabaseSettings settings = resolveDatabaseSettings(environment);
        if (settings == null) {
            return;
        }

        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("spring.datasource.url", settings.jdbcUrl());

        if (StringUtils.hasText(settings.username())) {
            overrides.put("spring.datasource.username", settings.username());
        }
        if (StringUtils.hasText(settings.password())) {
            overrides.put("spring.datasource.password", settings.password());
        }

        environment.getPropertySources().remove(PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
    }

    static NormalizedDatabaseSettings resolveDatabaseSettings(ConfigurableEnvironment environment) {
        String candidateUrl = firstNonBlank(
                environment.getProperty("DATABASE_JDBC_URL"),
                environment.getProperty("SUPABASE_POOLER_JDBC_URL"),
                environment.getProperty("SUPABASE_POOLER_URL"),
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("spring.datasource.url")
        );

        if (!StringUtils.hasText(candidateUrl)) {
            return null;
        }

        String explicitUsername = firstNonBlank(
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("DB_USERNAME")
        );
        String explicitPassword = firstNonBlank(
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("DB_PASSWORD")
        );

        if (candidateUrl.startsWith("jdbc:postgresql://")) {
            warnIfDirectSupabaseHost(candidateUrl);
            return new NormalizedDatabaseSettings(candidateUrl, explicitUsername, explicitPassword);
        }

        if (candidateUrl.startsWith("postgres://") || candidateUrl.startsWith("postgresql://")) {
            return normalizePostgresUrl(candidateUrl, explicitUsername, explicitPassword);
        }

        return new NormalizedDatabaseSettings(candidateUrl, explicitUsername, explicitPassword);
    }

    static NormalizedDatabaseSettings normalizePostgresUrl(
            String databaseUrl,
            String explicitUsername,
            String explicitPassword
    ) {
        try {
            URI uri = new URI(databaseUrl);
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                throw new IllegalStateException("Database URL is missing a host.");
            }

            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = StringUtils.hasText(uri.getPath()) ? uri.getPath() : "/postgres";
            String query = ensureSslModeRequired(uri.getQuery());
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + (query == null ? "" : "?" + query);

            String username = explicitUsername;
            String password = explicitPassword;

            if (uri.getUserInfo() != null) {
                String[] parts = uri.getUserInfo().split(":", 2);
                if (!StringUtils.hasText(username) && parts.length > 0) {
                    username = decode(parts[0]);
                }
                if (!StringUtils.hasText(password) && parts.length > 1) {
                    password = decode(parts[1]);
                }
            }

            warnIfDirectSupabaseHost(jdbcUrl);
            return new NormalizedDatabaseSettings(jdbcUrl, username, password);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Invalid database URL format.", ex);
        }
    }

    private static String ensureSslModeRequired(String query) {
        if (!StringUtils.hasText(query)) {
            return "sslmode=require";
        }
        if (query.contains("sslmode=")) {
            return query;
        }
        return query + "&sslmode=require";
    }

    private static void warnIfDirectSupabaseHost(String jdbcUrl) {
        if (jdbcUrl.contains(".supabase.co") && !jdbcUrl.contains(".pooler.supabase.com")) {
            log.warn(
                    "Production datasource is using a direct Supabase host. On IPv4-only platforms such as Render, "
                            + "prefer the Supabase session pooler host (*.pooler.supabase.com) to avoid connection timeouts."
            );
        }
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    record NormalizedDatabaseSettings(String jdbcUrl, String username, String password) {
    }
}
