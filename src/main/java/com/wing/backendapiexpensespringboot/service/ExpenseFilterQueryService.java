package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ExpenseListItemDto;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.service.media.ImageKitMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseFilterQueryService {

    private final ExpenseService expenseService;
    private final ImageKitMediaService imageKitMediaService;
    private static final Comparator<ExpenseEntity> RECENT_FIRST = Comparator
            .comparing(
                    ExpenseEntity::getUpdatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(
                    ExpenseEntity::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(
                    ExpenseEntity::getDate,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(
                    ExpenseEntity::getId,
                    Comparator.nullsLast(Comparator.reverseOrder()));

    public List<ExpenseListItemDto> getFilteredExpenses(
            String firebaseUid,
            int offset,
            int limit,
            LocalDate dateFrom,
            LocalDate dateTo,
            OffsetDateTime updatedSince,
            UUID categoryId,
            String merchant,
            Double minAmount,
            Double maxAmount
    ) {
        QueryPagination.validate(offset, limit);

        boolean hasExplicitDateFilter = dateFrom != null || dateTo != null;
        LocalDate start = dateFrom != null ? dateFrom : LocalDate.of(1970, 1, 1);
        LocalDate end = dateTo != null ? dateTo : LocalDate.now(java.time.ZoneOffset.UTC);

        List<ExpenseEntity> baseRecords;
        if (updatedSince != null) {
            baseRecords = expenseService.getExpensesChangedSince(firebaseUid, updatedSince);
        } else if (hasExplicitDateFilter) {
            baseRecords = expenseService.getExpensesBetween(firebaseUid, start, end);
        } else {
            baseRecords = expenseService.getExpenses(firebaseUid);
        }

        List<ExpenseEntity> filtered = baseRecords.stream()
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .filter(e ->
                        !hasExplicitDateFilter
                                || isWithinDateRange(e, start, end))
                .filter(e -> categoryId == null || categoryId.equals(e.getCategoryId()))
                .filter(e -> merchant == null || merchant.isBlank() || containsIgnoreCase(e.getMerchant(), merchant))
                .filter(e -> minAmount == null || e.getAmount() >= minAmount)
                .filter(e -> maxAmount == null || e.getAmount() <= maxAmount)
                .sorted(RECENT_FIRST)
                .toList();

        return QueryPagination.slice(filtered, offset, limit).stream()
                .map(this::toDto)
                .toList();
    }

    public List<ExpenseListItemDto> getFilteredExpensesForExport(
            String firebaseUid,
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID categoryId,
            String merchant,
            Double minAmount,
            Double maxAmount
    ) {
        return getFilteredExpenses(
                firebaseUid,
                0,
                1000,
                dateFrom,
                dateTo,
                null,
                categoryId,
                merchant,
                minAmount,
                maxAmount
        );
    }

    private ExpenseListItemDto toDto(ExpenseEntity entity) {
        return ExpenseListItemDto.builder()
                .id(entity.getId())
                .amount(entity.getAmount())
                .transactionType(entity.getTransactionType())
                .currency(entity.getCurrency())
                .merchant(entity.getMerchant())
                .date(formatExpenseDate(entity))
                .note(entity.getNote())
                .noteSummary(entity.getNoteSummary())
                .categoryId(entity.getCategoryId())
                .recurringExpenseId(entity.getRecurringExpenseId())
                .receiptPaths(imageKitMediaService.toDisplayReceiptUrls(entity.getReceiptPaths()))
                .originalAmount(entity.getOriginalAmount() == null ? null : entity.getOriginalAmount().doubleValue())
                .exchangeRate(entity.getExchangeRate() == null ? null : entity.getExchangeRate().doubleValue())
                .rateSource(entity.getRateSource())
                .isDeleted(Boolean.TRUE.equals(entity.getIsDeleted()))
                .deletedAt(entity.getDeletedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private boolean containsIgnoreCase(String value, String query) {
        if (value == null || query == null) {
            return false;
        }
        return value.toLowerCase().contains(query.toLowerCase().trim());
    }

    private boolean isWithinDateRange(ExpenseEntity entity, LocalDate start, LocalDate end) {
        LocalDate expenseDate = toUtcDate(entity);
        return expenseDate != null && !expenseDate.isBefore(start) && !expenseDate.isAfter(end);
    }

    private String formatExpenseDate(ExpenseEntity entity) {
        return entity.getDate() == null
                ? null
                : entity.getDate().withOffsetSameInstant(java.time.ZoneOffset.UTC).toString();
    }

    private LocalDate toUtcDate(ExpenseEntity entity) {
        return entity.getDate() == null
                ? null
                : entity.getDate().withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDate();
    }
}
