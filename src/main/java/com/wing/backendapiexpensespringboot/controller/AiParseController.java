package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.ParseRequest;
import com.wing.backendapiexpensespringboot.dto.ParseResponse;
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
public class AiParseController {

    private final AiOrchestratorService aiOrchestratorService;

    @PostMapping("/parse")
    public ResponseEntity<ParseResponse> parse(
            @Valid @RequestBody ParseRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        ParseResponse response = aiOrchestratorService.parse(user.getFirebaseUid(), request);
        return ResponseEntity.ok(response);
    }
}
