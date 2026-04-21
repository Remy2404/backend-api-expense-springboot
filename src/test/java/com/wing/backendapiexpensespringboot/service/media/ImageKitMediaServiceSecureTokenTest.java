package com.wing.backendapiexpensespringboot.service.media;

import com.wing.backendapiexpensespringboot.config.ImageKitProperties;
import com.wing.backendapiexpensespringboot.dto.media.ImageUploadAuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageKitMediaServiceSecureTokenTest {

    @Mock
    private ImageKitProperties imageKitProperties;

    @InjectMocks
    private ImageKitMediaService imageKitMediaService;

    @BeforeEach
    void setUp() {
        when(imageKitProperties.isEnabled()).thenReturn(true);
        when(imageKitProperties.getPublicKey()).thenReturn("public_test_key");
        when(imageKitProperties.getPrivateKey()).thenReturn("private_test_key");
        when(imageKitProperties.getUrlEndpoint()).thenReturn("https://ik.imagekit.io/test");
        when(imageKitProperties.getUploadTokenTtlSeconds()).thenReturn(3600L);
        when(imageKitProperties.getMaxFileSizeBytes()).thenReturn(5_242_880L);
        when(imageKitProperties.getAllowedMimeTypes()).thenReturn(List.of("image/jpeg", "image/png"));
        when(imageKitProperties.isPrivateFile()).thenReturn(true);
        when(imageKitProperties.isUseUniqueFileName()).thenReturn(true);
    }

    @Test
    void generatedTokenIsNotNull() {
        ImageUploadAuthResponse response = imageKitMediaService.createUploadAuth("user123");

        assertThat(response.token()).isNotNull();
    }

    @Test
    void generatedTokenIsNotEmpty() {
        ImageUploadAuthResponse response = imageKitMediaService.createUploadAuth("user123");

        assertThat(response.token()).isNotEmpty();
    }

    @Test
    void generatedTokenHasCorrectLength() {
        ImageUploadAuthResponse response = imageKitMediaService.createUploadAuth("user123");

        // SecureRandom generates 16 bytes, converted to 32 hex characters
        assertThat(response.token()).hasSize(32);
    }

    @Test
    void generatedTokenContainsOnlyHexCharacters() {
        ImageUploadAuthResponse response = imageKitMediaService.createUploadAuth("user123");

        assertThat(response.token()).matches("^[0-9a-f]{32}$");
    }

    @Test
    void consecutiveTokensAreUnique() {
        ImageUploadAuthResponse response1 = imageKitMediaService.createUploadAuth("user123");
        ImageUploadAuthResponse response2 = imageKitMediaService.createUploadAuth("user123");

        assertThat(response1.token()).isNotEqualTo(response2.token());
    }

    @Test
    void multipleTokensAreAllUnique() {
        Set<String> tokens = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            ImageUploadAuthResponse response = imageKitMediaService.createUploadAuth("user" + i);
            tokens.add(response.token());
        }

        // All 100 tokens should be unique
        assertThat(tokens).hasSize(100);
    }

    @Test
    void tokenIsNotUuidFormat() {
        ImageUploadAuthResponse response = imageKitMediaService.createUploadAuth("user123");

        // UUID format has dashes: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        // SecureRandom token should not have dashes
        assertThat(response.token()).doesNotContain("-");
    }

    @Test
    void tokenHasHighEntropy() {
        ImageUploadAuthResponse response = imageKitMediaService.createUploadAuth("user123");
        String token = response.token();

        // Check that token has variety of characters (not all same character)
        Set<Character> uniqueChars = new HashSet<>();
        for (char c : token.toCharArray()) {
            uniqueChars.add(c);
        }

        // Should have at least 8 different hex characters for good entropy
        assertThat(uniqueChars.size()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void tokenGenerationIsConsistentAcrossMultipleCalls() {
        // Generate multiple tokens and verify they all meet the security requirements
        for (int i = 0; i < 10; i++) {
            ImageUploadAuthResponse response = imageKitMediaService.createUploadAuth("user" + i);

            assertThat(response.token())
                    .isNotNull()
                    .hasSize(32)
                    .matches("^[0-9a-f]{32}$")
                    .doesNotContain("-");
        }
    }
}
