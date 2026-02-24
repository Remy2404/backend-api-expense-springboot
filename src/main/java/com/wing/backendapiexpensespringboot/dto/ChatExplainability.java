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
public class ChatExplainability {

    @Builder.Default
    private String summary = "";

    @Builder.Default
    private List<String> factors = List.of();

    private String correctionTip;
}
