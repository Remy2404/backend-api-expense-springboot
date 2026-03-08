package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.config.RealtimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RealtimeTokenService {

    private final RealtimeConfig realtimeConfig;

    public long expiresAtEpochSeconds() {
        return Instant.now().getEpochSecond() + Math.max(realtimeConfig.getTokenTtlSeconds(), 60L);
    }

    public String issueToken(String firebaseUid) {
        long expiresAt = expiresAtEpochSeconds();
        String payload = firebaseUid + "." + expiresAt;
        return payload + "." + sign(payload);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(realtimeConfig.getRelaySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign realtime token", exception);
        }
    }
}
