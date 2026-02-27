package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.RecurringExpenseDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.RecurringExpenseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recurring-expenses")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseQueryService recurringExpenseQueryService;

    @GetMapping
    public ResponseEntity<List<RecurringExpenseDto>> listRecurringExpenses(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        return ResponseEntity.ok(
                recurringExpenseQueryService.getRecurringExpenses(
                        requireFirebaseUid(user),
                        offset,
                        limit,
                        includeArchived
                )
        );
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }
}
