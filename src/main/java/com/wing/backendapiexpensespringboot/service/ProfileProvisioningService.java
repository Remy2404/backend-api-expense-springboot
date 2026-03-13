package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.model.ProfileEntity;
import com.wing.backendapiexpensespringboot.repository.ProfileRepository;
import com.wing.backendapiexpensespringboot.security.AppRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProfileProvisioningService {

    private static final String DEFAULT_RISK_LEVEL = "low";

    private final ProfileRepository profileRepository;

    @Transactional
    public AppRole syncProfile(
            String firebaseUid,
            String email,
            String displayName,
            String photoUrl,
            AppRole claimedRole
    ) {
        LocalDateTime now = LocalDateTime.now();
        ProfileEntity profile = profileRepository.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> ProfileEntity.builder()
                        .firebaseUid(firebaseUid)
                        .role(claimedRole.name())
                        .createdAt(now)
                        .build());

        boolean shouldPersist = profile.getId() == null;

        shouldPersist |= mergeField(profile.getEmail(), normalize(email), profile::setEmail);
        shouldPersist |= mergeField(profile.getDisplayName(), normalize(displayName), profile::setDisplayName);
        shouldPersist |= mergeField(profile.getPhotoUrl(), normalize(photoUrl), profile::setPhotoUrl);
        if (!StringUtils.hasText(profile.getRole())) {
            profile.setRole(claimedRole.name());
            shouldPersist = true;
        }
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
            shouldPersist = true;
        }
        if (profile.getAiEnabled() == null) {
            profile.setAiEnabled(Boolean.TRUE);
            shouldPersist = true;
        }
        if (!StringUtils.hasText(profile.getRiskLevel())) {
            profile.setRiskLevel(DEFAULT_RISK_LEVEL);
            shouldPersist = true;
        }

        if (!shouldPersist) {
            return AppRole.from(profile.getRole());
        }

        profile.setUpdatedAt(now);

        ProfileEntity savedProfile = profileRepository.save(profile);
        return AppRole.from(savedProfile.getRole());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean mergeField(String currentValue, String nextValue, java.util.function.Consumer<String> setter) {
        if (nextValue != null || currentValue == null) {
            if (!java.util.Objects.equals(currentValue, nextValue)) {
                setter.accept(nextValue);
                return true;
            }
        }
        return false;
    }
}
