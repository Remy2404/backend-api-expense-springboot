package com.wing.backendapiexpensespringboot.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExpenseMutationRequestDto {
    private String clientId;
    private String clientCreatedAt;
    private Double amount;
    private String transactionType;
    private String categoryId;
    private String date;
    private String notes;
    private String merchant;
    private String noteSummary;
    private String currency;
    private Double originalAmount;
    private Double exchangeRate;
    private String rateSource;
    private List<String> receiptPaths = new ArrayList<>();
    private String recurringExpenseId;
}
