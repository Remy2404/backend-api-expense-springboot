package com.wing.backendapiexpensespringboot.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DashboardSummaryResponseDto {
    double totalIncome;
    double totalExpense;
    double balance;
    long transactionCount;
    double monthlyIncome;
    double monthlyExpense;
}
