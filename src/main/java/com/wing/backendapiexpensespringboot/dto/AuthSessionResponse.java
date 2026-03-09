package com.wing.backendapiexpensespringboot.dto;

import lombok.Builder;

@Builder
public record AuthSessionResponse(
        String firebaseUid,
        String email,
        String role,
        Long expiresAtEpochSeconds,
        String firebaseCustomToken
) {
}
