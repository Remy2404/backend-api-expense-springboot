package com.wing.backendapiexpensespringboot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto {
    private UUID id;
    private String email;
    private String displayName;
    private String photoUrl;
    private BigDecimal initialBalance;
    private BigDecimal currentBalance;
    private String role;
    private String riskLevel;
    private Boolean aiEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
