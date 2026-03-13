package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.model.ProfileEntity;
import com.wing.backendapiexpensespringboot.repository.ProfileRepository;
import com.wing.backendapiexpensespringboot.security.AppRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileProvisioningServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileProvisioningService profileProvisioningService;

    @Test
    void syncProfileSetsAiEnabledTrueForNewProfile() {
        String firebaseUid = "firebase-uid-1";
        String email = "qa@example.com";

        when(profileRepository.findByFirebaseUid(firebaseUid)).thenReturn(Optional.empty());
        when(profileRepository.save(any(ProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppRole result = profileProvisioningService.syncProfile(
                firebaseUid,
                email,
                "QA User",
                "https://example.com/avatar.png",
                AppRole.USER
        );

        ArgumentCaptor<ProfileEntity> profileCaptor = ArgumentCaptor.forClass(ProfileEntity.class);

        assertEquals(AppRole.USER, result);
        verify(profileRepository).save(profileCaptor.capture());
        assertEquals(Boolean.TRUE, profileCaptor.getValue().getAiEnabled());
        assertEquals("low", profileCaptor.getValue().getRiskLevel());
    }

    @Test
    void syncProfileBackfillsAiEnabledWhenExistingProfileHasNull() {
        String firebaseUid = "firebase-uid-2";
        ProfileEntity existingProfile = ProfileEntity.builder()
                .firebaseUid(firebaseUid)
                .role(AppRole.USER.name())
                .aiEnabled(null)
                .build();

        when(profileRepository.findByFirebaseUid(firebaseUid)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(ProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        profileProvisioningService.syncProfile(
                firebaseUid,
                "user2@example.com",
                "User Two",
                null,
                AppRole.USER
        );

        assertNotNull(existingProfile.getAiEnabled());
        assertTrue(existingProfile.getAiEnabled());
    }

    @Test
    void syncProfileBackfillsRiskLevelWhenExistingProfileHasNull() {
        String firebaseUid = "firebase-uid-3";
        ProfileEntity existingProfile = ProfileEntity.builder()
                .firebaseUid(firebaseUid)
                .role(AppRole.USER.name())
                .riskLevel(null)
                .build();

        when(profileRepository.findByFirebaseUid(firebaseUid)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(ProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        profileProvisioningService.syncProfile(
                firebaseUid,
                "user3@example.com",
                "User Three",
                null,
                AppRole.USER
        );

        assertEquals("low", existingProfile.getRiskLevel());
    }

        @Test
        void syncProfileSkipsSaveWhenExistingProfileIsAlreadyUpToDate() {
                String firebaseUid = "firebase-uid-4";
                ProfileEntity existingProfile = ProfileEntity.builder()
                                .id(java.util.UUID.randomUUID())
                                .firebaseUid(firebaseUid)
                                .email("user4@example.com")
                                .displayName("User Four")
                                .photoUrl("https://example.com/photo4.png")
                                .role(AppRole.USER.name())
                                .aiEnabled(Boolean.TRUE)
                                .riskLevel("low")
                                .createdAt(java.time.LocalDateTime.now().minusDays(1))
                                .updatedAt(java.time.LocalDateTime.now().minusHours(1))
                                .build();

                when(profileRepository.findByFirebaseUid(firebaseUid)).thenReturn(Optional.of(existingProfile));

                AppRole role = profileProvisioningService.syncProfile(
                                firebaseUid,
                                "user4@example.com",
                                "User Four",
                                "https://example.com/photo4.png",
                                AppRole.USER
                );

                assertEquals(AppRole.USER, role);
                verify(profileRepository, never()).save(any(ProfileEntity.class));
        }
}
