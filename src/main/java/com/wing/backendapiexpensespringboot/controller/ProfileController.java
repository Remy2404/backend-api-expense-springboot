package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.ProfileDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.ProfileQueryService;
import com.wing.backendapiexpensespringboot.service.UserOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileQueryService profileQueryService;
    private final UserOnboardingService userOnboardingService;

    @GetMapping("/me")
    public ResponseEntity<ProfileDto> getCurrentProfile(@AuthenticationPrincipal UserPrincipal user) {
        userOnboardingService.ensureProfileReady(user);
        return ResponseEntity.ok(profileQueryService.getCurrentProfile(requireFirebaseUid(user)));
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
