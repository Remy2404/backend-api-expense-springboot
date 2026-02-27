package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.SavingsGoalDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.SavingsGoalQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class SavingsGoalController {

    private final SavingsGoalQueryService savingsGoalQueryService;

    @GetMapping
    public ResponseEntity<List<SavingsGoalDto>> listGoals(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(savingsGoalQueryService.getGoals(requireFirebaseUid(user)));
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
