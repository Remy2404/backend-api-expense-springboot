package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioDelta {

    private String category;

    @Builder.Default
    private Double baselineTotal = 0.0;

    @Builder.Default
    private Double projectedTotal = 0.0;

    @Builder.Default
    private Double delta = 0.0;

    @Builder.Default
    private Double confidence = 0.0;
}
