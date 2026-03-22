package com.wing.backendapiexpensespringboot.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.SessionCookieOptions;
import com.wing.backendapiexpensespringboot.config.AppConfig;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.service.ProfileProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseAuthenticationService {

    private static final long MIN_SESSION_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long MAX_SESSION_TTL_MILLIS = 14L * 24 * 60 * 60 * 1000L;

    private final FirebaseAuth firebaseAuth;
    private final AppConfig appConfig;
    private final ProfileProvisioningService profileProvisioningService;
    private final ObjectProvider<RoleLookupService> roleLookupServiceProvider;

    public AuthenticatedFirebaseUser authenticate(String idToken, boolean synchronizeProfile) {
        if (!StringUtils.hasText(idToken)) {
            throw AppException.unauthorized("Missing authentication token.");
        }

        try {
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken.trim(), true);
            return toAuthenticatedUser(decodedToken, synchronizeProfile);
        } catch (FirebaseAuthException exception) {
            throw AppException.unauthorized("Invalid or expired Firebase token.");
        }
    }

    public String createSessionCookie(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw AppException.unauthorized("Missing authentication token.");
        }

        long expiresInMillis = resolveSessionCookieMaxAgeMillis();
        SessionCookieOptions options = SessionCookieOptions.builder()
                .setExpiresIn(expiresInMillis)
                .build();

        try {
            return firebaseAuth.createSessionCookie(idToken.trim(), options);
        } catch (FirebaseAuthException exception) {
            throw AppException.unauthorized("Failed to create Firebase session cookie.");
        }
    }

    public AuthenticatedFirebaseUser authenticateSessionCookie(String sessionCookie, boolean synchronizeProfile) {
        if (!StringUtils.hasText(sessionCookie)) {
            throw AppException.unauthorized("Missing session cookie.");
        }

        try {
            FirebaseToken decodedToken = firebaseAuth.verifySessionCookie(sessionCookie.trim(), true);
            return toAuthenticatedUser(decodedToken, synchronizeProfile);
        } catch (FirebaseAuthException exception) {
            throw AppException.unauthorized("Invalid or expired Firebase session cookie.");
        }
    }

    public void revokeRefreshTokens(String firebaseUid) {
        if (!StringUtils.hasText(firebaseUid)) {
            return;
        }

        try {
            firebaseAuth.revokeRefreshTokens(firebaseUid.trim());
        } catch (FirebaseAuthException exception) {
            throw AppException.internalError("Failed to revoke Firebase refresh tokens.");
        }
    }

    public String issueCustomToken(UserPrincipal principal) {
        if (principal == null || !StringUtils.hasText(principal.getFirebaseUid())) {
            throw AppException.unauthorized("Missing authenticated user.");
        }

        Map<String, Object> customClaims = new HashMap<>();
        if (StringUtils.hasText(principal.getRole())) {
            customClaims.put("role", principal.getRole());
        }

        try {
            return firebaseAuth.createCustomToken(principal.getFirebaseUid(), customClaims);
        } catch (FirebaseAuthException exception) {
            throw AppException.internalError("Failed to issue Firebase custom token.");
        }
    }

    private AuthenticatedFirebaseUser toAuthenticatedUser(FirebaseToken decodedToken, boolean synchronizeProfile) {
        String firebaseUid = decodedToken.getUid();
        if (!StringUtils.hasText(firebaseUid)) {
            throw AppException.unauthorized("Token missing user identity.");
        }

        Map<String, Object> claims = extractClaims(decodedToken);
        String email = firstNonBlank(decodedToken.getEmail(), claimAsText(claims, "email"));
        String displayName = claimAsText(claims, "name");
        String photoUrl = claimAsText(claims, "picture");
        AppRole claimedRole = AppRole.from(claims.get("role"));

        AppRole resolvedRole = synchronizeProfile
                ? profileProvisioningService.syncProfile(firebaseUid, email, displayName, photoUrl, claimedRole)
                : resolveRole(firebaseUid, claims, claimedRole);

        UserPrincipal principal = UserPrincipal.builder()
                .firebaseUid(firebaseUid)
                .email(email)
                .role(resolvedRole.name())
                .claims(claims)
                .build();

        return new AuthenticatedFirebaseUser(principal, resolveExpiresAtEpochSeconds(claims));
    }

    private Map<String, Object> extractClaims(FirebaseToken decodedToken) {
        Map<String, Object> claims = decodedToken.getClaims();
        if (claims == null || claims.isEmpty()) {
            return Map.of();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(claims));
    }

    private AppRole resolveRole(String firebaseUid, Map<String, Object> claims, AppRole claimedRole) {
        if (claims.containsKey("role")) {
            return claimedRole;
        }

        RoleLookupService lookupService = roleLookupServiceProvider.getIfAvailable();
        if (lookupService == null) {
            return AppRole.USER;
        }

        return lookupService.findRoleByFirebaseUid(firebaseUid)
                .map(AppRole::from)
                .orElse(AppRole.USER);
    }

    private long resolveExpiresAtEpochSeconds(Map<String, Object> claims) {
        Object rawExpiration = claims.get("exp");
        if (rawExpiration instanceof Number number) {
            return number.longValue();
        }

        if (rawExpiration instanceof String expirationValue && StringUtils.hasText(expirationValue)) {
            try {
                return Long.parseLong(expirationValue.trim());
            } catch (NumberFormatException ignored) {
                // Fall back to the configured TTL when the exp claim is malformed.
            }
        }

        return Instant.now().getEpochSecond() + appConfig.getAuth().getFallbackMaxAgeSeconds();
    }

    private long resolveSessionCookieMaxAgeMillis() {
        long configuredSeconds = appConfig.getAuth().getSessionMaxAgeSeconds();
        long configuredMillis = configuredSeconds * 1000L;
        if (configuredMillis < MIN_SESSION_TTL_MILLIS || configuredMillis > MAX_SESSION_TTL_MILLIS) {
            throw AppException.internalError("app.auth.session-max-age-seconds must be between 300 and 1209600.");
        }
        return configuredMillis;
    }

    private String claimAsText(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value == null ? null : firstNonBlank(String.valueOf(value));
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }

        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }

        return null;
    }

    public record AuthenticatedFirebaseUser(UserPrincipal principal, long expiresAtEpochSeconds) {
        public long maxAgeSeconds() {
            return Math.max(expiresAtEpochSeconds - Instant.now().getEpochSecond(), 0L);
        }
    }
}
