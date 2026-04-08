package com.wing.backendapiexpensespringboot.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CategorySeedRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public int insertMissingDefaultCategories(String firebaseUid, List<DefaultCategorySeed> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return 0;
        }

        StringBuilder sql = new StringBuilder("""
                insert into categories (
                    id,
                    firebase_uid,
                    name,
                    icon,
                    color,
                    is_default,
                    sort_order,
                    created_at,
                    updated_at,
                    synced_at,
                    sync_status,
                    category_type
                ) values
                """);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("firebaseUid", firebaseUid);

        for (int index = 0; index < definitions.size(); index++) {
            DefaultCategorySeed definition = definitions.get(index);
            if (index > 0) {
                sql.append(", ");
            }

            sql.append("(:id").append(index)
                    .append(", :firebaseUid, :name").append(index)
                    .append(", :icon").append(index)
                    .append(", :color").append(index)
                    .append(", true, :sortOrder").append(index)
                    .append(", now(), now(), now(), 'synced', :categoryType").append(index)
                    .append(")");

            parameters.addValue("id" + index, UUID.randomUUID());
            parameters.addValue("name" + index, definition.name());
            parameters.addValue("icon" + index, definition.icon());
            parameters.addValue("color" + index, definition.color());
            parameters.addValue("sortOrder" + index, definition.sortOrder());
            parameters.addValue("categoryType" + index, definition.categoryType());
        }

        sql.append("""
                 on conflict (firebase_uid, name, category_type)
                 where is_deleted = false
                 do nothing
                """);
        return jdbcTemplate.update(sql.toString(), parameters);
    }

    public record DefaultCategorySeed(
            String name,
            String icon,
            String color,
            String categoryType,
            int sortOrder) {
    }
}
