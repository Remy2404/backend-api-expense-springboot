package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.ExpenseMutationRequestDto;
import com.wing.backendapiexpensespringboot.dto.ExpenseListItemDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.ExpenseFilterQueryService;
import com.wing.backendapiexpensespringboot.service.ExpenseService;
import com.wing.backendapiexpensespringboot.service.RealtimeRelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseFilterQueryService expenseFilterQueryService;
    private final ExpenseService expenseService;
    private final RealtimeRelayService realtimeRelayService;

    @GetMapping
    public ResponseEntity<List<ExpenseListItemDto>> listExpenses(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String updatedSince,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String merchant,
            @RequestParam(required = false) Double minAmount,
            @RequestParam(required = false) Double maxAmount) {
        return ResponseEntity.ok(expenseFilterQueryService.getFilteredExpenses(
                requireFirebaseUid(user),
                offset,
                limit,
                parseLocalDate(dateFrom, "dateFrom"),
                parseLocalDate(dateTo, "dateTo"),
                parseLocalDateTime(updatedSince, "updatedSince"),
                parseUuid(categoryId, "categoryId"),
                merchant,
                minAmount,
                maxAmount));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseListItemDto> getExpense(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable("id") String expenseIdRaw) {
        UUID expenseId = parseUuid(expenseIdRaw, "id");
        String firebaseUid = requireFirebaseUid(user);
        ExpenseEntity expense = expenseService.getExpenseById(firebaseUid, expenseId);
        return ResponseEntity.ok(toDto(expense));
    }

    @PostMapping
    public ResponseEntity<ExpenseListItemDto> createExpense(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody ExpenseMutationRequestDto request) {
        String firebaseUid = requireFirebaseUid(user);
        ExpenseEntity created = expenseService.createExpense(firebaseUid, request);
        realtimeRelayService.publishSyncInvalidation(firebaseUid, List.of("expenses"), "expense_created");
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseListItemDto> updateExpense(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable("id") String expenseIdRaw,
            @RequestBody ExpenseMutationRequestDto request) {
        UUID expenseId = parseUuid(expenseIdRaw, "id");
        String firebaseUid = requireFirebaseUid(user);
        ExpenseEntity updated = expenseService.updateExpense(firebaseUid, expenseId, request);
        realtimeRelayService.publishSyncInvalidation(firebaseUid, List.of("expenses"), "expense_updated");
        return ResponseEntity.ok(toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ExpenseListItemDto> deleteExpense(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable("id") String expenseIdRaw) {
        UUID expenseId = parseUuid(expenseIdRaw, "id");
        String firebaseUid = requireFirebaseUid(user);
        ExpenseEntity deleted = expenseService.softDeleteExpense(firebaseUid, expenseId);
        realtimeRelayService.publishSyncInvalidation(firebaseUid, List.of("expenses"), "expense_deleted");
        return ResponseEntity.ok(toDto(deleted));
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

    private LocalDateTime parseLocalDateTime(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException exception) {
            throw AppException.badRequest(fieldName + " must use ISO-8601 date-time format");
        }
    }

    private ExpenseListItemDto toDto(ExpenseEntity entity) {
        return ExpenseListItemDto.builder()
                .id(entity.getId())
                .amount(entity.getAmount())
                .transactionType(entity.getTransactionType())
                .currency(entity.getCurrency())
                .merchant(entity.getMerchant())
                .date(entity.getDate() == null ? null
                        : entity.getDate().withOffsetSameInstant(ZoneOffset.UTC).toString())
                .note(entity.getNote())
                .noteSummary(entity.getNoteSummary())
                .categoryId(entity.getCategoryId())
                .recurringExpenseId(entity.getRecurringExpenseId())
                .receiptPaths(entity.getReceiptPaths())
                .originalAmount(entity.getOriginalAmount() == null ? null : entity.getOriginalAmount().doubleValue())
                .exchangeRate(entity.getExchangeRate() == null ? null : entity.getExchangeRate().doubleValue())
                .rateSource(entity.getRateSource())
                .isDeleted(Boolean.TRUE.equals(entity.getIsDeleted()))
                .deletedAt(entity.getDeletedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
