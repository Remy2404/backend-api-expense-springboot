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
public class CorrectResponse {

    @Builder.Default
    private Boolean memoryUpdated = false;

    @Builder.Default
    private Integer newOverrideCount = 0;

    @Builder.Default
    private Double confidence = 0.0;

    private String learningSummary;
    private String learnedMerchant;

    @Builder.Default
    private Boolean needsConfirmation = false;

    @Builder.Default
    private List<String> safetyWarnings = List.of();
}
