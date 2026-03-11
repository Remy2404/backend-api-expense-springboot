package com.wing.backendapiexpensespringboot.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BudgetSummaryResponseDto {
    double budgetLimit;
    double spent;
    double remaining;
}
