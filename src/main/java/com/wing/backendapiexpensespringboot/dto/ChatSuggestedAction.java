package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSuggestedAction {

    private String id;

    @Builder.Default
    private String label = "";

    @Builder.Default
    private String prompt = "";

    private String icon;
}
