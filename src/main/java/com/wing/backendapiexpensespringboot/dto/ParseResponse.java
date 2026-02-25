package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResponse {

    private Double amount;
    private String currency;
    private String merchant;
    private LocalDate date;
    private String note;
    private String noteSummary;
    private String suggestedCategoryId;

    @Builder.Default
    private Double confidence = 0.0;

    private String source; // "gemini", "memory", "gemini_vision"

    @Builder.Default
    private Boolean needsConfirmation = false;

    private String geminiModel;

    @Builder.Default
    private List<String> safetyWarnings = List.of();
}
