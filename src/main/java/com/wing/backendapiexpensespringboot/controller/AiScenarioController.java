package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.ScenarioRequest;
import com.wing.backendapiexpensespringboot.dto.ScenarioResponse;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.ScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiScenarioController {

    private final ScenarioService scenarioService;

    @PostMapping("/scenario")
    public ResponseEntity<ScenarioResponse> runScenario(
            @Valid @RequestBody ScenarioRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        ScenarioResponse response = scenarioService.runScenario(user.getFirebaseUid(), request);
        return ResponseEntity.ok(response);
    }
}
