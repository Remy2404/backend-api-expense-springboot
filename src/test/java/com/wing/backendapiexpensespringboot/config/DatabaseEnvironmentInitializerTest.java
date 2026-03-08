package com.wing.backendapiexpensespringboot.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DatabaseEnvironmentInitializerTest {

    @Test
    void resolveDatabaseSettingsConvertsStandardPostgresUrlToJdbc() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://postgres:test-pass@aws-1-ap-south-1.pooler.supabase.com:5432/postgres");

        DatabaseEnvironmentInitializer.NormalizedDatabaseSettings settings =
                DatabaseEnvironmentInitializer.resolveDatabaseSettings(environment);

        assertEquals(
                "jdbc:postgresql://aws-1-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require",
                settings.jdbcUrl()
        );
        assertEquals("postgres", settings.username());
        assertEquals("test-pass", settings.password());
    }

    @Test
    void resolveDatabaseSettingsPrefersExplicitJdbcUrl() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_JDBC_URL", "jdbc:postgresql://pooler.supabase.com:5432/postgres?sslmode=require")
                .withProperty("DB_USERNAME", "db-user")
                .withProperty("DB_PASSWORD", "db-pass");

        DatabaseEnvironmentInitializer.NormalizedDatabaseSettings settings =
                DatabaseEnvironmentInitializer.resolveDatabaseSettings(environment);

        assertEquals("jdbc:postgresql://pooler.supabase.com:5432/postgres?sslmode=require", settings.jdbcUrl());
        assertEquals("db-user", settings.username());
        assertEquals("db-pass", settings.password());
    }

    @Test
    void resolveDatabaseSettingsStripsWrappingQuotesFromEnvValues() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_JDBC_URL", "\"jdbc:postgresql://pooler.supabase.com:5432/postgres?sslmode=require\"")
                .withProperty("DB_USERNAME", "\"db-user\"")
                .withProperty("DB_PASSWORD", "\"db-pass\"");

        DatabaseEnvironmentInitializer.NormalizedDatabaseSettings settings =
                DatabaseEnvironmentInitializer.resolveDatabaseSettings(environment);

        assertEquals("jdbc:postgresql://pooler.supabase.com:5432/postgres?sslmode=require", settings.jdbcUrl());
        assertEquals("db-user", settings.username());
        assertEquals("db-pass", settings.password());
    }

    @Test
    void resolveDatabaseSettingsReturnsNullWhenNoUrlIsPresent() {
        assertNull(DatabaseEnvironmentInitializer.resolveDatabaseSettings(new MockEnvironment()));
    }
}
