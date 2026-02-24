package com.wing.backendapiexpensespringboot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioRequest {

    @NotBlank(message = "category is required")
    @Size(min = 1, max = 100, message = "category must be between 1 and 100 characters")
    private String category;

    @Builder.Default
    private Double deltaAmount = 0.0;

    @Builder.Default
    @Min(value = 7, message = "periodDays must be at least 7")
    @Max(value = 90, message = "periodDays must be at most 90")
    private Integer periodDays = 30;
}
