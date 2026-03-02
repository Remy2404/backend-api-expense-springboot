package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionItemDto {
    private UUID id;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private UUID categoryId;
    private String note;
    private LocalDate date;
    private LocalDateTime createdAt;
}
