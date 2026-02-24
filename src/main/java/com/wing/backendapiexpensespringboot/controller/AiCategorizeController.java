package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.CategorizeRequest;
import com.wing.backendapiexpensespringboot.dto.CategorizeResponse;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.AiOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiCategorizeController {

    private final AiOrchestratorService aiOrchestratorService;

    @PostMapping("/categorize")
    public ResponseEntity<CategorizeResponse> categorize(
            @Valid @RequestBody CategorizeRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        CategorizeResponse response = aiOrchestratorService.categorize(user.getFirebaseUid(), request);
        return ResponseEntity.ok(response);
    }
}
