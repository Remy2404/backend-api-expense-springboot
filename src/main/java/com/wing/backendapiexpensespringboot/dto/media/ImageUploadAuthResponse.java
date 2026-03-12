package com.wing.backendapiexpensespringboot.dto.media;

import lombok.Builder;

import java.util.List;

@Builder
public record ImageUploadAuthResponse(
        String token,
        long expire,
        String signature,
        String publicKey,
        String urlEndpoint,
        String uploadFolder,
        long maxFileSizeBytes,
        List<String> allowedMimeTypes,
        boolean privateFile,
        boolean useUniqueFileName
) {
}
