package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.media.ImageUploadAuthResponse;
import com.wing.backendapiexpensespringboot.dto.media.SignedMediaUrlResponse;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.media.ImageKitMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final ImageKitMediaService imageKitMediaService;

    @GetMapping("/upload-auth")
    public ResponseEntity<ImageUploadAuthResponse> getUploadAuth(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(imageKitMediaService.createUploadAuth(requireFirebaseUid(user)));
    }

    @GetMapping("/signed-url")
    public ResponseEntity<SignedMediaUrlResponse> getSignedUrl(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam("path") String path) {
        return ResponseEntity.ok(imageKitMediaService.createSignedUrl(requireFirebaseUid(user), path));
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
