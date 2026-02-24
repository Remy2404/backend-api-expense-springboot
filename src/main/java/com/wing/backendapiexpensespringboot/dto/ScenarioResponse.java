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
public class ScenarioResponse {

    private String scenarioLabel;

    @Builder.Default
    private List<ScenarioDelta> deltas = List.of();

    @Builder.Default
    private Double projectedMonthTotal = 0.0;

    @Builder.Default
    private Double projectedSavings = 0.0;

    private String dataConfidence; // "high", "medium", "low", "insufficient"

    private String disclaimer;

    @Builder.Default
    private Boolean needsConfirmation = false;

    @Builder.Default
    private List<String> safetyWarnings = List.of();
}
