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

        mergeField(profile.getEmail(), normalize(email), profile::setEmail);
        mergeField(profile.getDisplayName(), normalize(displayName), profile::setDisplayName);
        mergeField(profile.getPhotoUrl(), normalize(photoUrl), profile::setPhotoUrl);
        if (!StringUtils.hasText(profile.getRole())) {
            profile.setRole(claimedRole.name());
        }
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
        }
        profile.setUpdatedAt(now);

        ProfileEntity savedProfile = profileRepository.save(profile);
        return AppRole.from(savedProfile.getRole());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void mergeField(String currentValue, String nextValue, java.util.function.Consumer<String> setter) {
        if (nextValue != null || currentValue == null) {
            setter.accept(nextValue);
        }
    }
}
