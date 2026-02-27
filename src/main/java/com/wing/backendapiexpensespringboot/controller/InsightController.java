package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.InsightDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.InsightQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightQueryService insightQueryService;

    @GetMapping
    public ResponseEntity<List<InsightDto>> listInsights(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(insightQueryService.getInsights(requireFirebaseUid(user), offset, limit));
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
