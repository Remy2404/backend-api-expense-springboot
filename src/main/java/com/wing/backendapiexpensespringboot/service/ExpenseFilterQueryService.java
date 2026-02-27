package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ExpenseListItemDto;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseFilterQueryService {

    private final ExpenseService expenseService;

    public List<ExpenseListItemDto> getFilteredExpenses(
            String firebaseUid,
            int offset,
            int limit,
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID categoryId,
            String merchant,
            Double minAmount,
            Double maxAmount
    ) {
        QueryPagination.validate(offset, limit);

        LocalDate start = dateFrom != null ? dateFrom : LocalDate.now().minusYears(5);
        LocalDate end = dateTo != null ? dateTo : LocalDate.now();

        List<ExpenseEntity> filtered = expenseService.getExpensesBetween(firebaseUid, start, end).stream()
                .filter(e -> categoryId == null || categoryId.equals(e.getCategoryId()))
                .filter(e -> merchant == null || merchant.isBlank() || containsIgnoreCase(e.getMerchant(), merchant))
                .filter(e -> minAmount == null || e.getAmount() >= minAmount)
                .filter(e -> maxAmount == null || e.getAmount() <= maxAmount)
                .sorted(Comparator.comparing(ExpenseEntity::getDate).reversed())
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
                .currency(entity.getCurrency())
                .merchant(entity.getMerchant())
                .date(entity.getDate())
                .note(entity.getNote())
                .noteSummary(entity.getNoteSummary())
                .categoryId(entity.getCategoryId())
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
}
