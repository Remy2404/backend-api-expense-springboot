package com.wing.backendapiexpensespringboot.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class FinanceSummaryResponseDto {
    String period;
    LocalDate periodStart;
    LocalDate periodEnd;
    long transactionCount;
    double totalIncome;
    double totalExpense;
    double balance;
}
