package com.wing.backendapiexpensespringboot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false)
    private String firebaseUid;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "merchant")
    private String merchant;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "notes")
    private String note;

    @Column(name = "note_summary")
    private String noteSummary;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "ai_category_id")
    private UUID aiCategoryId;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "ai_source")
    private String aiSource;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
