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
public class CategorizeRequest {

    private UUID expenseId;

    @NotBlank(message = "merchant is required")
    @Size(min = 1, max = 255, message = "merchant must be between 1 and 255 characters")
    private String merchant;

    @Size(max = 2000, message = "note must be at most 2000 characters")
    private String note;
}
