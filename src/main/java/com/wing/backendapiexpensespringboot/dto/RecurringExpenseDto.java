package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private OffsetDateTime lastGenerated;
    private OffsetDateTime nextDueDate;
    private Boolean isActive;
    private Boolean notificationEnabled;
    private Integer notificationDaysBefore;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
