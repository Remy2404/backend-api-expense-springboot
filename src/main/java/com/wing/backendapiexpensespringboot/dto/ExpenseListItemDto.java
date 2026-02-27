package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseListItemDto {
    private UUID id;
    private Double amount;
    private String currency;
    private String merchant;
    private LocalDate date;
    private String note;
    private String noteSummary;
    private UUID categoryId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
