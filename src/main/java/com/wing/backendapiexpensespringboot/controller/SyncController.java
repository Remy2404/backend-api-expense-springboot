package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.SyncPullResponseDto;
import com.wing.backendapiexpensespringboot.dto.SyncPushRequestDto;
import com.wing.backendapiexpensespringboot.dto.SyncPushResponseDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
public class SyncController {

    private static final LocalDateTime DEFAULT_SINCE = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final SyncService syncService;

    @PostMapping("/push")
    public ResponseEntity<SyncPushResponseDto> push(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody(required = false) SyncPushRequestDto request
    ) {
        SyncPushRequestDto safeRequest = request == null ? new SyncPushRequestDto() : request;
        return ResponseEntity.ok(syncService.push(requireFirebaseUid(user), safeRequest));
    }

    @GetMapping("/pull")
    public ResponseEntity<SyncPullResponseDto> pull(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(name = "expense_since", required = false) String expenseSinceRaw,
            @RequestParam(name = "category_since", required = false) String categorySinceRaw,
            @RequestParam(name = "budget_since", required = false) String budgetSinceRaw,
            @RequestParam(name = "goal_since", required = false) String goalSinceRaw,
            @RequestParam(name = "recurring_since", required = false) String recurringSinceRaw
    ) {
        return ResponseEntity.ok(syncService.pull(
                requireFirebaseUid(user),
                parseSince(expenseSinceRaw, "expense_since"),
                parseSince(categorySinceRaw, "category_since"),
                parseSince(budgetSinceRaw, "budget_since"),
                parseSince(goalSinceRaw, "goal_since"),
                parseSince(recurringSinceRaw, "recurring_since")
        ));
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }

    private LocalDateTime parseSince(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SINCE;
        }

        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(raw).atOffset(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
        }

        throw AppException.badRequest(fieldName + " must be a valid ISO datetime");
    }
}
