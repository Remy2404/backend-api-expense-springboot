package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.FinanceSummaryResponseDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FinanceSummaryService {

    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public FinanceSummaryResponseDto getSummary(String firebaseUid, String periodRaw) {
        SummaryPeriod period = SummaryPeriod.from(periodRaw);
        LocalDate now = LocalDate.now(ZoneOffset.UTC);

        LocalDate periodStart = null;
        LocalDate periodEnd = null;
        if (period == SummaryPeriod.THIS_MONTH) {
            periodStart = now.withDayOfMonth(1);
            periodEnd = now.withDayOfMonth(now.lengthOfMonth());
        }

        ExpenseRepository.FinanceSummaryAggregate aggregate =
                period == SummaryPeriod.THIS_MONTH
                        ? expenseRepository.summarizeByFirebaseUidAndDateBetween(
                                firebaseUid,
                                toStartOfDayUtc(periodStart),
                                toStartOfNextDayUtc(periodEnd))
                        : expenseRepository.summarizeByFirebaseUid(firebaseUid);
        double totalIncome = toDouble(aggregate == null ? null : aggregate.getTotalIncome());
        double totalExpense = toDouble(aggregate == null ? null : aggregate.getTotalExpense());
        long transactionCount = toLong(aggregate == null ? null : aggregate.getTransactionCount());

        return FinanceSummaryResponseDto.builder()
                .period(period.value)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .transactionCount(transactionCount)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome - totalExpense)
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

    private OffsetDateTime toStartOfDayUtc(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime toStartOfNextDayUtc(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private enum SummaryPeriod {
        ALL_TIME("all-time"),
        THIS_MONTH("this-month");

        private final String value;

        SummaryPeriod(String value) {
            this.value = value;
        }

        private static SummaryPeriod from(String raw) {
            if (raw == null || raw.isBlank()) {
                return ALL_TIME;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            for (SummaryPeriod candidate : values()) {
                if (candidate.value.equals(normalized)) {
                    return candidate;
                }
            }
            throw AppException.badRequest("period must be one of: all-time, this-month");
        }
    }
}
