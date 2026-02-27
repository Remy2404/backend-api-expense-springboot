package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsightDto {
    private UUID id;
    private String insightType;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String summaryText;
    private Map<String, Object> dataSnapshot;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
