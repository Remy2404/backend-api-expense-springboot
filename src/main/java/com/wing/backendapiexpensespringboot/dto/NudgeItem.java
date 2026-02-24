package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NudgeItem {

    private String id;

    private String type; // "budget_risk", "spending_spike", "bill_reminder", "savings_opportunity"

    @Builder.Default
    private String title = "";

    @Builder.Default
    private String body = "";

    private String actionPrompt;

    private String severity; // "info", "warning", "critical"

    private LocalDateTime generatedAt;
}
