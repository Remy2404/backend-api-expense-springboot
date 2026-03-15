package com.wing.backendapiexpensespringboot.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NudgesResponse {

    @Builder.Default
    private List<NudgeItem> nudges = List.of();

    private OffsetDateTime generatedAt;

    @Builder.Default
    private Boolean needsConfirmation = false;

    @Builder.Default
    private List<String> safetyWarnings = List.of();
}
