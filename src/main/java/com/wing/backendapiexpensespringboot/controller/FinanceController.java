package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.FinanceSummaryResponseDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.FinanceSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceSummaryService financeSummaryService;

    @GetMapping("/summary")
    public ResponseEntity<FinanceSummaryResponseDto> summary(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(name = "period", required = false, defaultValue = "all-time") String period
    ) {
        return ResponseEntity.ok(financeSummaryService.getSummary(requireFirebaseUid(user), period));
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
