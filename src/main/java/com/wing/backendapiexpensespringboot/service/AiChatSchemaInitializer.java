package com.wing.backendapiexpensespringboot.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiChatSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_chat_messages (
                    id UUID PRIMARY KEY,
                    firebase_uid VARCHAR(255) NOT NULL,
                    role VARCHAR(32) NOT NULL,
                    content TEXT NOT NULL,
                    request_id VARCHAR(128),
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_ai_chat_messages_uid_created_at
                ON ai_chat_messages (firebase_uid, created_at DESC)
                """);
        log.info("Ensured ai_chat_messages schema exists");
    }
}
