package com.wing.backendapiexpensespringboot.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SyncPullResponseDto {
    private List<ExpenseItem> expenses;
    private List<CategoryItem> categories;
    private List<BudgetItem> budgets;
    private List<GoalItem> goals;
    private List<RecurringItem> recurring;

    public static SyncPullResponseDto empty() {
        return SyncPullResponseDto.builder()
                .expenses(new ArrayList<>())
                .categories(new ArrayList<>())
                .budgets(new ArrayList<>())
                .goals(new ArrayList<>())
                .recurring(new ArrayList<>())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ExpenseItem {
        private String id;
        private Double amount;
        private String transactionType;
        private String categoryId;
        private String date;
        private String notes;
        private String merchant;
        private String noteSummary;
        private String aiCategoryId;
        private Double aiConfidence;
        private String aiSource;
        private String aiLastUpdated;
        private String recurringExpenseId;
        private List<String> receiptPaths;
        private String currency;
        private Double originalAmount;
        private Double exchangeRate;
        private String rateSource;
        private String createdAt;
        private String updatedAt;
        private String syncedAt;
        private Boolean isDeleted;
        private String deletedAt;
        private Integer retryCount;
        private String lastError;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class CategoryItem {
        private String id;
        private String name;
        private String icon;
        private String color;
        private Boolean isDefault;
        private String categoryType;
        private Integer sortOrder;
        private String createdAt;
        private String updatedAt;
        private String syncedAt;
        private Boolean isDeleted;
        private String deletedAt;
        private Integer retryCount;
        private String lastError;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class BudgetItem {
        private String id;
        private String month;
        private Double totalAmount;
        private List<CategoryBudgetItem> categoryBudgets;
        private String createdAt;
        private String updatedAt;
        private String syncedAt;
        private Boolean isDeleted;
        private String deletedAt;
        private Integer retryCount;
        private String lastError;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class CategoryBudgetItem {
        private String categoryId;
        private Double amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GoalItem {
        private String id;
        private String name;
        private Double targetAmount;
        private Double currentAmount;
        private String deadline;
        private String color;
        private String icon;
        private Boolean isArchived;
        private List<GoalTransactionItem> transactions;
        private String createdAt;
        private String updatedAt;
        private String syncedAt;
        private Boolean isDeleted;
        private String deletedAt;
        private Integer retryCount;
        private String lastError;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GoalTransactionItem {
        private String id;
        private Double amount;
        private String type;
        private String note;
        private String date;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RecurringItem {
        private String id;
        private Double amount;
        private String categoryId;
        private String notes;
        private String frequency;
        private String currency;
        private Double originalAmount;
        private Double exchangeRate;
        private String startDate;
        private String endDate;
        private String lastGenerated;
        private String nextDueDate;
        private Boolean isActive;
        private Boolean notificationEnabled;
        private Integer notificationDaysBefore;
        private String createdAt;
        private String updatedAt;
        private String syncedAt;
        private Boolean isDeleted;
        private String deletedAt;
        private Integer retryCount;
        private String lastError;
    }
}
