package com.wing.backendapiexpensespringboot.service.media;

import com.wing.backendapiexpensespringboot.config.ImageKitProperties;
import com.wing.backendapiexpensespringboot.dto.media.ImageUploadAuthResponse;
import com.wing.backendapiexpensespringboot.dto.media.SignedMediaUrlResponse;
import com.wing.backendapiexpensespringboot.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImageKitMediaService {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final long MAX_UPLOAD_AUTH_TTL_SECONDS = 3599L;
    private static final int MAX_RECEIPTS_PER_EXPENSE = 10;

    // Security Fix: Use SecureRandom for cryptographically secure token generation
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ImageKitProperties imageKitProperties;

    public ImageUploadAuthResponse createUploadAuth(String firebaseUid) {
        requireConfiguredForUpload();

        long now = Instant.now().getEpochSecond();
        long expire = now + Math.min(
                Math.max(imageKitProperties.getUploadTokenTtlSeconds(), 60L),
                MAX_UPLOAD_AUTH_TTL_SECONDS);
        // Security Fix: Replace insecure UUID.randomUUID() with cryptographically secure token
        String token = generateSecureToken();
        String signature = signHex(token + expire, imageKitProperties.getPrivateKey());

        return ImageUploadAuthResponse.builder()
                .token(token)
                .expire(expire)
                .signature(signature)
                .publicKey(imageKitProperties.getPublicKey().trim())
                .urlEndpoint(normalizeEndpoint(imageKitProperties.getUrlEndpoint()))
                .uploadFolder(buildUserReceiptFolder(firebaseUid))
                .maxFileSizeBytes(imageKitProperties.getMaxFileSizeBytes())
                .allowedMimeTypes(List.copyOf(imageKitProperties.getAllowedMimeTypes()))
                .privateFile(imageKitProperties.isPrivateFile())
                .useUniqueFileName(imageKitProperties.isUseUniqueFileName())
                .build();
    }

    public SignedMediaUrlResponse createSignedUrl(String firebaseUid, String rawPath) {
        requireConfiguredForUpload();
        String normalizedPath = normalizeIncomingReceiptPath(firebaseUid, rawPath);
        long expiresAt = Instant.now().getEpochSecond() + Math.max(imageKitProperties.getSignedUrlTtlSeconds(), 60L);
        String signedUrl = buildSignedDeliveryUrl(normalizedPath, expiresAt);
        return SignedMediaUrlResponse.builder()
                .path(normalizedPath)
                .url(signedUrl)
                .expiresAtEpochSeconds(expiresAt)
                .build();
    }

    public List<String> normalizeIncomingReceiptPaths(String firebaseUid, List<String> receiptPaths) {
        if (receiptPaths == null) {
            return null;
        }

        Set<String> deduped = new LinkedHashSet<>();
        for (String rawPath : receiptPaths) {
            if (!StringUtils.hasText(rawPath)) {
                continue;
            }
            deduped.add(normalizeIncomingReceiptPath(firebaseUid, rawPath));
        }

        if (deduped.size() > MAX_RECEIPTS_PER_EXPENSE) {
            throw AppException.badRequest("receipt_paths supports up to " + MAX_RECEIPTS_PER_EXPENSE + " files");
        }

        return new ArrayList<>(deduped);
    }

    public List<String> toDisplayReceiptUrls(List<String> receiptPaths) {
        if (receiptPaths == null || receiptPaths.isEmpty()) {
            return receiptPaths == null ? null : List.of();
        }
        if (!isConfigured()) {
            return receiptPaths;
        }

        List<String> resolved = new ArrayList<>(receiptPaths.size());
        for (String rawPath : receiptPaths) {
            if (!StringUtils.hasText(rawPath)) {
                continue;
            }

            String normalizedPath = normalizeImageKitPathIfPossible(rawPath);
            if (normalizedPath == null) {
                resolved.add(rawPath);
                continue;
            }

            long expiresAt = Instant.now().getEpochSecond() + Math.max(imageKitProperties.getSignedUrlTtlSeconds(), 60L);
            resolved.add(buildSignedDeliveryUrl(normalizedPath, expiresAt));
        }
        return resolved;
    }

    private String normalizeIncomingReceiptPath(String firebaseUid, String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw AppException.badRequest("receipt path cannot be blank");
        }
        if (rawPath.startsWith("file://") || rawPath.startsWith("content://")) {
            throw AppException.badRequest("receipt_paths must contain uploaded remote files");
        }

        String normalizedPath = normalizeImageKitPathIfPossible(rawPath);
        if (!StringUtils.hasText(normalizedPath)) {
            throw AppException.badRequest("receipt_paths must reference ImageKit file paths");
        }

        String allowedPrefix = buildUserReceiptFolder(firebaseUid) + "/";
        if (!normalizedPath.startsWith(allowedPrefix)) {
            throw AppException.unauthorized("Receipt path is outside the authenticated user folder");
        }
        return normalizedPath;
    }

    private String normalizeImageKitPathIfPossible(String rawPath) {
        String trimmed = rawPath.trim();
        if (trimmed.startsWith("/")) {
            return trimmed;
        }

        URI endpointUri = endpointUri();
        try {
            URI valueUri = URI.create(trimmed);
            if (!StringUtils.hasText(valueUri.getScheme()) || !StringUtils.hasText(valueUri.getHost())) {
                return null;
            }

            boolean sameScheme = valueUri.getScheme().equalsIgnoreCase(endpointUri.getScheme());
            boolean sameHost = valueUri.getHost().equalsIgnoreCase(endpointUri.getHost());
            int valuePort = valueUri.getPort() == -1 ? endpointUri.getPort() : valueUri.getPort();
            int endpointPort = endpointUri.getPort();
            if (!sameScheme || !sameHost || valuePort != endpointPort) {
                return null;
            }

            String endpointPath = normalizePath(endpointUri.getPath());
            String valuePath = normalizePath(valueUri.getPath());

            if (!StringUtils.hasText(endpointPath) || "/".equals(endpointPath)) {
                return valuePath;
            }

            if (valuePath.equals(endpointPath)) {
                return "/";
            }

            if (valuePath.startsWith(endpointPath + "/")) {
                return valuePath.substring(endpointPath.length());
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private String buildSignedDeliveryUrl(String normalizedPath, long expiresAtEpochSeconds) {
        String endpoint = normalizeEndpoint(imageKitProperties.getUrlEndpoint());
        String endpointWithSlash = ensureTrailingSlash(endpoint);
        String pathWithoutLeadingSlash = normalizedPath.startsWith("/")
                ? normalizedPath.substring(1)
                : normalizedPath;

        String unsignedUrl = endpointWithSlash + pathWithoutLeadingSlash;
        String stringToSign = pathWithoutLeadingSlash + expiresAtEpochSeconds;
        String signature = signHex(stringToSign, imageKitProperties.getPrivateKey());
        return unsignedUrl + "?ik-t=" + expiresAtEpochSeconds + "&ik-s=" + signature;
    }

    private String buildUserReceiptFolder(String firebaseUid) {
        String baseFolder = normalizePath(imageKitProperties.getReceiptFolder());
        String safeUserId = firebaseUid
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
        String folder = ensureNoTrailingSlash(baseFolder) + "/" + safeUserId;
        return folder.startsWith("/") ? folder : "/" + folder;
    }

    private String signHex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA1_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception exception) {
            throw AppException.internalError("Failed to sign ImageKit payload");
        }
    }

    private String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte b : value) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private URI endpointUri() {
        try {
            return URI.create(normalizeEndpoint(imageKitProperties.getUrlEndpoint()));
        } catch (IllegalArgumentException exception) {
            throw AppException.internalError("imagekit.url-endpoint is invalid");
        }
    }

    private String normalizeEndpoint(String value) {
        if (!StringUtils.hasText(value)) {
            throw AppException.internalError("ImageKit URL endpoint is not configured");
        }
        return ensureNoTrailingSlash(value.trim());
    }

    private String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return "/";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private String ensureNoTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return end == 0 ? value : value.substring(0, end);
    }

    private boolean isConfigured() {
        return imageKitProperties.isEnabled()
                && StringUtils.hasText(imageKitProperties.getPublicKey())
                && StringUtils.hasText(imageKitProperties.getPrivateKey())
                && StringUtils.hasText(imageKitProperties.getUrlEndpoint());
    }

    private void requireConfiguredForUpload() {
        if (!imageKitProperties.isEnabled()) {
            throw AppException.notFound("Media upload is disabled");
        }
        if (!isConfigured()) {
            throw AppException.internalError("ImageKit is not configured");
        }
    }

    /**
     * Security Fix: Generate cryptographically secure random token.
     * Uses SecureRandom instead of UUID.randomUUID() for better security.
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[16];
        SECURE_RANDOM.nextBytes(randomBytes);

        // Convert to hex string (similar format to UUID but cryptographically secure)
        StringBuilder token = new StringBuilder(32);
        for (byte b : randomBytes) {
            token.append(String.format("%02x", b));
        }

        return token.toString();
    }
}
