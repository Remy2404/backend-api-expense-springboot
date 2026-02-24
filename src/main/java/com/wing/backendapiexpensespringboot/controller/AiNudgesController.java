package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.NudgesResponse;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.NudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiNudgesController {

    private final NudgeService nudgeService;

    @GetMapping("/nudges")
    public ResponseEntity<NudgesResponse> getNudges(
            @AuthenticationPrincipal UserPrincipal user) {

        NudgesResponse response = nudgeService.getNudges(user.getFirebaseUid());
        return ResponseEntity.ok(response);
    }
}
