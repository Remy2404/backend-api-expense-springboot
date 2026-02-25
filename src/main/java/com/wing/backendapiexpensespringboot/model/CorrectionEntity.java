package com.wing.backendapiexpensespringboot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_corrections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false)
    private String firebaseUid;

    @Column(name = "expense_id")
    private UUID expenseId;

    @Column(name = "original_category_id")
    private UUID originalCategoryId;

    @Column(name = "corrected_category_id", nullable = false)
    private UUID correctedCategoryId;

    @Column(name = "original_amount")
    private BigDecimal originalAmount;

    @Column(name = "corrected_amount")
    private Double correctedAmount;

    @Column(name = "original_merchant")
    private String originalMerchant;

    @Column(name = "corrected_merchant")
    private String correctedMerchant;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
