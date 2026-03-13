package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetDto {
    private UUID id;
    private String month;
    private BigDecimal totalAmount;
    private List<CategoryBudgetDto> categoryBudgets;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
