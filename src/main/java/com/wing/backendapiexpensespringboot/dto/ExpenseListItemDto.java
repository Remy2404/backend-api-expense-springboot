package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseListItemDto {
    private UUID id;
    private Double amount;
    private String transactionType;
    private String currency;
    private String merchant;
    private String date;
    private String note;
    private String noteSummary;
    private UUID categoryId;
    private UUID recurringExpenseId;
    private List<String> receiptPaths;
    private Double originalAmount;
    private Double exchangeRate;
    private String rateSource;
    private Boolean isDeleted;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
