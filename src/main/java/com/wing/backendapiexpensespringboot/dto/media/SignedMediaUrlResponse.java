package com.wing.backendapiexpensespringboot.dto.media;

import lombok.Builder;

@Builder
public record SignedMediaUrlResponse(
        String path,
        String url,
        long expiresAtEpochSeconds
) {
}
