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
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'ai_chat_messages'
                """, Integer.class);
        if (tableCount == null || tableCount == 0) {
            log.error("Missing required table: public.ai_chat_messages. Apply schema migrations before startup.");
            throw new IllegalStateException("Missing required table public.ai_chat_messages");
        }
        log.info("Verified required table exists: public.ai_chat_messages");
    }
}
