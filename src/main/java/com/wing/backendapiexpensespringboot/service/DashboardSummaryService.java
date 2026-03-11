package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.DashboardSummaryResponseDto;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponseDto getSummary(String firebaseUid) {
        ExpenseRepository.FinanceSummaryAggregate allTimeAggregate = expenseRepository.summarizeByFirebaseUid(firebaseUid);
        ExpenseRepository.FinanceSummaryAggregate currentMonthAggregate = expenseRepository
                .summarizeByFirebaseUidAndDateBetween(
                        firebaseUid,
                        firstDayOfCurrentMonthUtc(),
                        firstDayOfNextMonthUtc());

        double totalIncome = toDouble(allTimeAggregate == null ? null : allTimeAggregate.getTotalIncome());
        double totalExpense = toDouble(allTimeAggregate == null ? null : allTimeAggregate.getTotalExpense());
        long transactionCount = toLong(allTimeAggregate == null ? null : allTimeAggregate.getTransactionCount());
        double monthlyIncome = toDouble(currentMonthAggregate == null ? null : currentMonthAggregate.getTotalIncome());
        double monthlyExpense = toDouble(currentMonthAggregate == null ? null : currentMonthAggregate.getTotalExpense());

        return DashboardSummaryResponseDto.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome - totalExpense)
                .transactionCount(transactionCount)
                .monthlyIncome(monthlyIncome)
                .monthlyExpense(monthlyExpense)
                .build();
    }

    private double toDouble(Number value) {
        if (value == null) {
            return 0.0d;
        }
        return value.doubleValue();
    }

    private long toLong(Number value) {
        if (value == null) {
            return 0L;
        }
        return value.longValue();
    }

    private OffsetDateTime firstDayOfCurrentMonthUtc() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return now.withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime firstDayOfNextMonthUtc() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return now.withDayOfMonth(1).plusMonths(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
