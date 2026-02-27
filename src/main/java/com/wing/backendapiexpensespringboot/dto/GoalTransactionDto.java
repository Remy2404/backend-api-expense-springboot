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
public class GoalTransactionDto {
    private UUID id;
    private UUID goalId;
    private BigDecimal amount;
    private String type;
    private String note;
    private LocalDateTime date;
}
