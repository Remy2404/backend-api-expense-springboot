package com.wing.backendapiexpensespringboot.dto;

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
public class ParseRequest {

    @NotBlank(message = "rawText is required")
    @Size(min = 1, max = 5000, message = "rawText must be between 1 and 5000 characters")
    private String rawText;

    @Builder.Default
    private String source = "text"; // "text", "voice", "receipt_image"
}
