package com.wing.backendapiexpensespringboot.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthSessionRequest(
        @NotBlank(message = "idToken is required.")
        String idToken
) {
}
