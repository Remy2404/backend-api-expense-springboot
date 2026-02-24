package com.wing.backendapiexpensespringboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectRequest {

    private UUID expenseId;
    private UUID originalCategoryId;

    @NotBlank(message = "correctedCategoryId is required")
    private UUID correctedCategoryId;

    @NotBlank(message = "merchant is required")
    @Size(min = 1, max = 255, message = "merchant must be between 1 and 255 characters")
    private String merchant;

    private Double originalAmount;
    private Double correctedAmount;
    private String originalMerchant;
    private String correctedMerchant;

    @Size(max = 300, message = "correctionReason must be at most 300 characters")
    private String correctionReason;

    @Size(max = 64, message = "correctionSource must be at most 64 characters")
    private String correctionSource;

    @Builder.Default
    private Boolean confirmAmountChange = false;
}
