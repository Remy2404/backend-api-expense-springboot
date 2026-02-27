package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.ExpenseListItemDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.ExpenseFilterQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseFilterQueryService expenseFilterQueryService;

    @GetMapping
    public ResponseEntity<List<ExpenseListItemDto>> listExpenses(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount
    ) {
        return ResponseEntity.ok(expenseFilterQueryService.getFilteredExpenses(
                requireFirebaseUid(user),
                offset,
                limit,
                parseLocalDate(dateFrom, "dateFrom"),
                parseLocalDate(dateTo, "dateTo"),
                parseUuid(categoryId, "categoryId"),
                merchant,
                minAmount,
                maxAmount
        ));
    }

    private String requireFirebaseUid(UserPrincipal user) {
        if (user == null || user.getFirebaseUid() == null || user.getFirebaseUid().isBlank()) {
            throw AppException.unauthorized("Missing authenticated user");
        }
        return user.getFirebaseUid();
    }

    private LocalDate parseLocalDate(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException exception) {
            throw AppException.badRequest(fieldName + " must use format YYYY-MM-DD");
        }
    }

    private UUID parseUuid(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw AppException.badRequest(fieldName + " must be a valid UUID");
        }
    }
}
