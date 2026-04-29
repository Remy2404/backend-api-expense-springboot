package com.wing.backendapiexpensespringboot.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DashboardSummaryResponseDto {
    double totalIncome;
    double totalExpense;
    double balance;
    long transactionCount;
    double monthlyIncome;
    double monthlyExpense;
    BudgetSummarySnapshot budgetSummary;
    List<RecentTransactionItem> recentTransactions;
    List<RecentCategoryItem> recentCategories;

    @Value
    @Builder
    public static class BudgetSummarySnapshot {
        String month;
        double budgetLimit;
        double spent;
        double remaining;
    }

    @Value
    @Builder
    public static class RecentTransactionItem {
        String id;
        Double amount;
        String transactionType;
        String currency;
        String merchant;
        String date;
        String note;
        String noteSummary;
        String categoryId;
        Boolean isDeleted;
        String categoryName;
    }

    @Value
    @Builder
    public static class RecentCategoryItem {
        String id;
        String name;
    }
}
