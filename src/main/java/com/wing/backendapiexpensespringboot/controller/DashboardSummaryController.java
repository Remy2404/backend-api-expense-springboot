package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.DashboardSummaryResponseDto;
import com.wing.backendapiexpensespringboot.dto.FinanceSummaryResponseDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.DashboardSummaryService;
import com.wing.backendapiexpensespringboot.service.FinanceSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardSummaryController {

    private final DashboardSummaryService dashboardSummaryService;
    private final FinanceSummaryService financeSummaryService;

    @GetMapping("/dashboard/summary")
    public ResponseEntity<DashboardSummaryResponseDto> dashboardSummary(
            @AuthenticationPrincipal UserPrincipal user
    ) {
        return ResponseEntity.ok(dashboardSummaryService.getSummary(requireFirebaseUid(user)));
    }

    @GetMapping("/dashboard-summary")
    public ResponseEntity<FinanceSummaryResponseDto> legacyDashboardSummary(
            @AuthenticationPrincipal UserPrincipal user
    ) {
        return ResponseEntity.ok(financeSummaryService.getSummary(requireFirebaseUid(user), "all-time"));
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
