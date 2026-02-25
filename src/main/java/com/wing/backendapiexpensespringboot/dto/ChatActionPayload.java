package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatActionPayload {

    private Double amount;
    private String category;
    private String categoryId;
    private String note;
    private String noteSummary;
    private String date;
    private String merchant;
}
