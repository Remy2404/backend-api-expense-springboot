package com.wing.backendapiexpensespringboot.repository;

import com.wing.backendapiexpensespringboot.security.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class ProfileUpsertRepository {

    private static final String UPSERT_PROFILE_SQL = """
            insert into profiles (
                firebase_uid,
                email,
                display_name,
                photo_url,
                role,
                ai_enabled,
                risk_level,
                sync_status,
                created_at,
                updated_at
            )
            values (
                :firebaseUid,
                :email,
                :displayName,
                :photoUrl,
                :claimedRole,
                false,
                'low',
                'pending',
                now(),
                now()
            )
            on conflict (firebase_uid) do update
            set email = coalesce(excluded.email, profiles.email),
                display_name = coalesce(excluded.display_name, profiles.display_name),
                photo_url = coalesce(excluded.photo_url, profiles.photo_url),
                role = coalesce(nullif(profiles.role, ''), excluded.role),
                ai_enabled = coalesce(profiles.ai_enabled, false),
                risk_level = coalesce(nullif(profiles.risk_level, ''), 'low'),
                sync_status = coalesce(nullif(profiles.sync_status, ''), 'pending'),
                updated_at = case
                    when excluded.email is distinct from profiles.email
                        or excluded.display_name is distinct from profiles.display_name
                        or excluded.photo_url is distinct from profiles.photo_url
                    then now()
                    else profiles.updated_at
                end
            returning role
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AppRole upsertProfile(
            String firebaseUid,
            String email,
            String displayName,
            String photoUrl,
            AppRole claimedRole) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("firebaseUid", normalize(firebaseUid))
                .addValue("email", normalize(email))
                .addValue("displayName", normalize(displayName))
                .addValue("photoUrl", normalize(photoUrl))
                .addValue("claimedRole", claimedRole == null ? AppRole.USER.name() : claimedRole.name());

        String persistedRole = jdbcTemplate.queryForObject(UPSERT_PROFILE_SQL, parameters, String.class);
        return AppRole.from(persistedRole);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
