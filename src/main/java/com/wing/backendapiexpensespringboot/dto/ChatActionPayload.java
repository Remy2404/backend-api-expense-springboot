package com.wing.backendapiexpensespringboot.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ChatActionPayload {

    private String kind;
    private String type;
    private Double amount;
    private String currency;
    private String category;
    private String categoryType;
    private String categoryId;
    private String note;
    private String noteSummary;
    private String date;
    private String merchant;
    private String month;
    private Double totalAmount;
    private String name;
    private Double targetAmount;
    private Double currentAmount;
    private String deadline;
    private String color;
    private String icon;
    private String frequency;
    private String startDate;
    private String endDate;
    private Boolean notificationEnabled;
    private Integer notificationDaysBefore;
}
