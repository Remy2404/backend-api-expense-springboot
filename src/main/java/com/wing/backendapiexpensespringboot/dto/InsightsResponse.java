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
public class InsightsResponse {

    private String insightType; // "daily", "weekly", "monthly"

    @Builder.Default
    private InsightPeriod period = new InsightPeriod();

    private String summary;

    @Builder.Default
    private List<InsightHighlight> highlights = List.of();

    @Builder.Default
    private Double confidence = 0.0;

    @Builder.Default
    private Boolean needsConfirmation = false;

    @Builder.Default
    private List<String> safetyWarnings = List.of();
}
