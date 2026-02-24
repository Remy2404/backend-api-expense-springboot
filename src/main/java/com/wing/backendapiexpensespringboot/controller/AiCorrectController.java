package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.CorrectRequest;
import com.wing.backendapiexpensespringboot.dto.CorrectResponse;
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
public class AiCorrectController {

    private final AiOrchestratorService aiOrchestratorService;

    @PostMapping("/correct")
    public ResponseEntity<CorrectResponse> correct(
            @Valid @RequestBody CorrectRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        CorrectResponse response = aiOrchestratorService.correct(user.getFirebaseUid(), request);
        return ResponseEntity.ok(response);
    }
}
