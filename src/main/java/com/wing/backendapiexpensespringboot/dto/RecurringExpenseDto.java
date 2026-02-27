package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExpenseDto {
    private UUID id;
    private BigDecimal amount;
    private UUID categoryId;
    private String notes;
    private String frequency;
    private String currency;
    private BigDecimal originalAmount;
    private BigDecimal exchangeRate;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime lastGenerated;
    private LocalDateTime nextDueDate;
    private Boolean isActive;
    private Boolean notificationEnabled;
    private Integer notificationDaysBefore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
