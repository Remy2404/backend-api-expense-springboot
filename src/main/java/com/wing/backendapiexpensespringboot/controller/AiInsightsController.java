package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.InsightsResponse;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.InsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiInsightsController {

    private final InsightsService insightsService;

    @GetMapping("/insights")
    public ResponseEntity<InsightsResponse> getInsights(
            @RequestParam String type,
            @AuthenticationPrincipal UserPrincipal user) {

        InsightsResponse response = insightsService.getInsights(user.getFirebaseUid(), type);
        return ResponseEntity.ok(response);
    }
}
