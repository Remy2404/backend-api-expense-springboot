package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.BudgetSummaryResponseDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class BudgetSummaryService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public BudgetSummaryResponseDto getBudgetSummary(String firebaseUid, String monthRaw) {
        YearMonth month = parseYearMonth(monthRaw);
        OffsetDateTime start = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endExclusive = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        BigDecimal budgetLimit = budgetRepository.findActiveByMonthAndFirebaseUid(month.toString(), firebaseUid)
                .map(BudgetEntity::getTotalAmount)
                .orElse(BigDecimal.ZERO);

        ExpenseRepository.FinanceSummaryAggregate aggregate = expenseRepository
                .summarizeByFirebaseUidAndDateBetween(firebaseUid, start, endExclusive);
        double spent = toDouble(aggregate == null ? null : aggregate.getTotalExpense());
        double limit = toDouble(budgetLimit);

        return BudgetSummaryResponseDto.builder()
                .budgetLimit(limit)
                .spent(spent)
                .remaining(limit - spent)
                .build();
    }

    private YearMonth parseYearMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            throw AppException.badRequest("month is required in format YYYY-MM");
        }
        try {
            return YearMonth.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw AppException.badRequest("month must use format YYYY-MM");
        }
    }

    private double toDouble(Number value) {
        if (value == null) {
            return 0.0d;
        }
        return value.doubleValue();
    }
}
