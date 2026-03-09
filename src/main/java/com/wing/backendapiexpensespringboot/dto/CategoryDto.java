package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private UUID id;
    private String name;
    private String icon;
    private String color;
    private Boolean isDefault;
    private String categoryType;
    private Integer sortOrder;
    private Boolean isDeleted;
    private String deletedAt;
    private Integer retryCount;
    private String lastError;
    private String createdAt;
    private String updatedAt;
    private String syncedAt;
}
