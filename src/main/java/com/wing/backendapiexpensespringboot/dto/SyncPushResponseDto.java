package com.wing.backendapiexpensespringboot.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SyncPushResponseDto {
    private SyncedItems syncedItems;
    private List<FailedItem> failedItems;
    private Map<String, String> categoryIdMap;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class SyncedItems {
        private int expenses;
        private int categories;
        private int budgets;
        private int goals;
        private int recurring;
        private int billSplit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class FailedItem {
        private String id;
        private String entityType;
        private String error;
    }

    public static SyncPushResponseDto empty() {
        return SyncPushResponseDto.builder()
                .syncedItems(SyncedItems.builder()
                        .expenses(0)
                        .categories(0)
                        .budgets(0)
                        .goals(0)
                        .recurring(0)
                        .billSplit(0)
                        .build())
                .failedItems(new ArrayList<>())
                .categoryIdMap(new HashMap<>())
                .build();
    }
}
