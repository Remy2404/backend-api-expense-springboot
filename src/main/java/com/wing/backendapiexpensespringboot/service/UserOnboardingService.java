package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.config.AsyncExecutionConfig;
import com.wing.backendapiexpensespringboot.security.AppRole;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserOnboardingService {

    private final ProfileProvisioningService profileProvisioningService;
    private final DefaultCategoryProvisioningService defaultCategoryProvisioningService;

    public void ensureProfileReady(UserPrincipal principal) {
        if (principal == null || !StringUtils.hasText(principal.getFirebaseUid())) {
            return;
        }

        profileProvisioningService.syncProfile(
                principal.getFirebaseUid(),
                principal.getEmail(),
                principal.getDisplayName(),
                principal.getPhotoUrl(),
                AppRole.from(principal.getRole()));
    }

    @Async(AsyncExecutionConfig.USER_BOOTSTRAP_EXECUTOR)
    public void warmUpAfterSessionCreated(UserPrincipal principal) {
        if (principal == null || !StringUtils.hasText(principal.getFirebaseUid())) {
            return;
        }

        try {
            ensureProfileReady(principal);
            defaultCategoryProvisioningService.provisionMissingDefaultCategories(principal.getFirebaseUid());
        } catch (RuntimeException exception) {
            log.warn(
                    "Background user bootstrap failed for {}: {}",
                    principal.getFirebaseUid(),
                    exception.getMessage());
        }
    }
}
