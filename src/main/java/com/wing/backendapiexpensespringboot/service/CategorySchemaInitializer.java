package com.wing.backendapiexpensespringboot.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategorySchemaInitializer {

    private static final String INDEX_NAME = "uq_categories_active_name_type";

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureCategoryUniqueIndex() {
        if (indexExists()) {
            log.info("Partial unique index {} already exists on categories", INDEX_NAME);
            return;
        }

        int merged = cleanExistingDuplicates();
        if (merged > 0) {
            log.info("Cleaned {} duplicate active categories before creating unique index", merged);
        }

        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS %s
                ON categories (firebase_uid, lower(name), lower(category_type))
                WHERE (is_deleted IS NOT TRUE)
                """.formatted(INDEX_NAME));

        log.info("Created partial unique index {} on categories", INDEX_NAME);
    }

    private boolean indexExists() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename  = 'categories'
                  AND indexname  = ?
                """, Integer.class, INDEX_NAME);
        return count != null && count > 0;
    }

    private int cleanExistingDuplicates() {
        return jdbcTemplate.update("""
                WITH ranked AS (
                    SELECT id,
                           ROW_NUMBER() OVER (
                               PARTITION BY firebase_uid, lower(name), lower(category_type)
                               ORDER BY created_at ASC NULLS LAST, id
                           ) AS rn
                    FROM categories
                    WHERE is_deleted IS NOT TRUE
                )
                UPDATE categories
                SET is_deleted  = TRUE,
                    deleted_at  = NOW(),
                    sync_status = 'synced'
                WHERE id IN (SELECT id FROM ranked WHERE rn > 1)
                """);
    }
}
