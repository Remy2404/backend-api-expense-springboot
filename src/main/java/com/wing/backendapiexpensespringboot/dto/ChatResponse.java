package com.wing.backendapiexpensespringboot.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ChatResponse {

    @Builder.Default
    private String answer = "";

    @Builder.Default
    private String queryUsed = "";

    @Builder.Default
    private Integer dataPoints = 0;

    @Builder.Default
    private Double confidence = 0.0;

    private String intent;

    @Builder.Default
    private Boolean silentAction = false;

    private ChatActionPayload payload;
    @Builder.Default
    private List<ChatActionPayload> transactions = List.of();
    private ChatExplainability explainability;

    @Builder.Default
    private List<ChatSuggestedAction> suggestedActions = List.of();

    @Builder.Default
    private Boolean needsConfirmation = false;

    @Builder.Default
    private List<String> safetyWarnings = List.of();

    private Map<String, Double> fieldConfidences;

    private String pendingActionId;

    private String actionType;

    @Builder.Default
    private List<String> missingFields = List.of();
}
