package com.wing.backendapiexpensespringboot.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @Builder.Default
    private String question = "";

    @Builder.Default
    private List<ChatHistoryMessage> history = List.of();

    @Size(max = 64, message = "timezone must be at most 64 characters")
    private String timezone;

    @Size(max = 64, message = "localNowIso must be at most 64 characters")
    private String localNowIso;

    @Builder.Default
    private Boolean imagePresent = false;

    @Size(max = 6000000, message = "attachmentBase64 must be at most 6000000 characters")
    private String attachmentBase64;

    private String attachmentMime; // "image/jpeg", "image/png", "image/webp"
}
