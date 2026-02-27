package com.wing.backendapiexpensespringboot.security;

import java.util.Optional;

/**
 * Optional extension point for DB-backed role lookup by Firebase UID.
 *
 * Provide your own @Component implementing this interface to resolve roles from DB.
 * If absent, role from Firebase custom claims is used, falling back to USER.
 */
public interface RoleLookupService {
    Optional<String> findRoleByFirebaseUid(String firebaseUid);
}
