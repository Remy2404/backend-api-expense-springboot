package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastResponse {

    @Builder.Default
    private Double estimatedMonthTotal = 0.0;

    @Builder.Default
    private Double estimatedSavings = 0.0;

    @Builder.Default
    private List<ForecastRiskCategory> riskCategories = List.of();

    private String dataConfidence; // "high", "medium", "low", "insufficient"

    @Builder.Default
    private Integer daysOfData = 0;

    @Builder.Default
    private Double confidence = 0.0;

    private String disclaimer;

    @Builder.Default
    private Boolean needsConfirmation = false;

    @Builder.Default
    private List<String> safetyWarnings = List.of();
}
