package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.config.RealtimeConfig;
import com.wing.backendapiexpensespringboot.dto.RealtimeSessionResponse;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.RealtimeTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeConfig realtimeConfig;
    private final RealtimeTokenService realtimeTokenService;

    @GetMapping("/session")
    public ResponseEntity<RealtimeSessionResponse> session(@AuthenticationPrincipal UserPrincipal user) {
        String firebaseUid = requireFirebaseUid(user);
        String socketUrl = resolveSocketUrl();
        long expiresAt = realtimeTokenService.expiresAtEpochSeconds();
        return ResponseEntity.ok(RealtimeSessionResponse.builder()
                .token(realtimeTokenService.issueToken(firebaseUid))
                .socketUrl(socketUrl)
                .expiresAtEpochSeconds(expiresAt)
                .build());
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }

    private String resolveSocketUrl() {
        String publicSocketUrl = realtimeConfig.normalizedPublicSocketUrl();
        if (publicSocketUrl != null && !publicSocketUrl.isBlank()) {
            return publicSocketUrl;
        }
        String relayUrl = realtimeConfig.normalizedRelayUrl();
        if (relayUrl != null && !relayUrl.isBlank()) {
            return relayUrl;
        }
        throw AppException.badRequest("Realtime socket URL is not configured");
    }
}
