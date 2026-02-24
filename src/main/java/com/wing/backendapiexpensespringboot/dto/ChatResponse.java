package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    @Builder.Default
    private String answer = "";

    @Builder.Default
    private String queryUsed = "";

    @Builder.Default
    private Integer dataPoints = 0;

    @Builder.Default
    private Double confidence = 0.0;

    private String intent; // "none", "add_expense", "query_expenses"

    @Builder.Default
    private Boolean silentAction = false;

    private ChatActionPayload payload;
    private ChatExplainability explainability;

    @Builder.Default
    private List<ChatSuggestedAction> suggestedActions = List.of();

    @Builder.Default
    private Boolean needsConfirmation = false;

    @Builder.Default
    private List<String> safetyWarnings = List.of();

    private Map<String, Double> fieldConfidences;
}
