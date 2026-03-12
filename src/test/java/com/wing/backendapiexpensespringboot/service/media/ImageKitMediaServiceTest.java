package com.wing.backendapiexpensespringboot.service.media;

import com.wing.backendapiexpensespringboot.config.ImageKitProperties;
import com.wing.backendapiexpensespringboot.dto.media.ImageUploadAuthResponse;
import com.wing.backendapiexpensespringboot.dto.media.SignedMediaUrlResponse;
import com.wing.backendapiexpensespringboot.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageKitMediaServiceTest {

    private ImageKitMediaService imageKitMediaService;

    @BeforeEach
    void setUp() {
        ImageKitProperties properties = new ImageKitProperties();
        properties.setEnabled(true);
        properties.setPublicKey("public_key");
        properties.setPrivateKey("private_key");
        properties.setUrlEndpoint("https://ik.example.com/demo");
        properties.setReceiptFolder("/receipts");
        properties.setAllowedMimeTypes(List.of("image/jpeg", "image/png"));
        properties.setMaxFileSizeBytes(10 * 1024 * 1024L);
        properties.setUploadTokenTtlSeconds(900);
        properties.setSignedUrlTtlSeconds(900);
        properties.setPrivateFile(true);
        properties.setUseUniqueFileName(true);
        imageKitMediaService = new ImageKitMediaService(properties);
    }

    @Test
    void createUploadAuthBuildsUserScopedFolder() {
        ImageUploadAuthResponse response = imageKitMediaService.createUploadAuth("User-123");

        assertNotNull(response.token());
        assertNotNull(response.signature());
        assertEquals("/receipts/user-123", response.uploadFolder());
    }

    @Test
    void normalizeIncomingReceiptPathsRejectsPathOutsideUserFolder() {
        assertThrows(AppException.class, () -> imageKitMediaService.normalizeIncomingReceiptPaths(
                "user-1",
                List.of("/receipts/user-2/file.jpg")));
    }

    @Test
    void createSignedUrlAddsRequiredSignatureQueryParameters() {
        SignedMediaUrlResponse response = imageKitMediaService.createSignedUrl(
                "user-1",
                "/receipts/user-1/file.jpg");

        assertEquals("/receipts/user-1/file.jpg", response.path());
        assertTrue(response.url().contains("ik-t="));
        assertTrue(response.url().contains("ik-s="));
    }
}
