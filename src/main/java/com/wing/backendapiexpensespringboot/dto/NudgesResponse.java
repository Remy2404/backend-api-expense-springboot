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
public class NudgesResponse {

    @Builder.Default
    private List<NudgeItem> nudges = List.of();

    @Builder.Default
    private Boolean needsConfirmation = false;

    @Builder.Default
    private List<String> safetyWarnings = List.of();
}
