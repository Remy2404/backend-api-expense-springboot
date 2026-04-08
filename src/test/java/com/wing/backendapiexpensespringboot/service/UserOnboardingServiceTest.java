package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserOnboardingServiceTest {

    @Mock
    private ProfileProvisioningService profileProvisioningService;

    @Mock
    private DefaultCategoryProvisioningService defaultCategoryProvisioningService;

    @InjectMocks
    private UserOnboardingService userOnboardingService;

    @Test
    void warmUpAfterSessionCreatedBootstrapsProfileThenCategories() {
        UserPrincipal principal = UserPrincipal.builder()
                .firebaseUid("firebase-uid-1")
                .email("qa@example.com")
                .displayName("QA User")
                .photoUrl("https://example.com/photo.png")
                .role("USER")
                .build();

        userOnboardingService.warmUpAfterSessionCreated(principal);

        verify(profileProvisioningService).syncProfile(
                "firebase-uid-1",
                "qa@example.com",
                "QA User",
                "https://example.com/photo.png",
                com.wing.backendapiexpensespringboot.security.AppRole.USER);
        verify(defaultCategoryProvisioningService).provisionMissingDefaultCategories("firebase-uid-1");
    }

    @Test
    void warmUpAfterSessionCreatedSwallowsProfileBootstrapFailures() {
        UserPrincipal principal = UserPrincipal.builder()
                .firebaseUid("firebase-uid-2")
                .email("qa@example.com")
                .role("USER")
                .build();

        doThrow(new IllegalStateException("database down"))
                .when(profileProvisioningService)
                .syncProfile("firebase-uid-2", "qa@example.com", null, null,
                        com.wing.backendapiexpensespringboot.security.AppRole.USER);

        userOnboardingService.warmUpAfterSessionCreated(principal);

        verify(defaultCategoryProvisioningService, never()).provisionMissingDefaultCategories("firebase-uid-2");
    }
}
