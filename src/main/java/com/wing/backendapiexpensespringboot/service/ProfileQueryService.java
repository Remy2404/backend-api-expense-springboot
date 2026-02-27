package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ProfileDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.ProfileEntity;
import com.wing.backendapiexpensespringboot.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileQueryService {

    private final ProfileRepository profileRepository;

    public ProfileDto getCurrentProfile(String firebaseUid) {
        ProfileEntity profile = profileRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> AppException.notFound("Profile not found"));

        return ProfileDto.builder()
                .id(profile.getId())
                .email(profile.getEmail())
                .displayName(profile.getDisplayName())
                .photoUrl(profile.getPhotoUrl())
                .initialBalance(profile.getInitialBalance())
                .currentBalance(profile.getCurrentBalance())
                .role(profile.getRole())
                .riskLevel(profile.getRiskLevel())
                .aiEnabled(profile.getAiEnabled())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
