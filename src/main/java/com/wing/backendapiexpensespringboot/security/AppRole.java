package com.wing.backendapiexpensespringboot.security;

import java.util.Locale;

public enum AppRole {
    USER,
    ADMIN;

    public static AppRole from(Object rawRole) {
        if (rawRole == null) {
            return USER;
        }

        String normalized = String.valueOf(rawRole)
                .replace("ROLE_", "")
                .trim()
                .toUpperCase(Locale.ROOT);

        try {
            return AppRole.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return USER;
        }
    }
}
