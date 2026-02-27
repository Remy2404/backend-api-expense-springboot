package com.wing.backendapiexpensespringboot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    @Column(name = "ai_last_updated")
    private LocalDateTime aiLastUpdated;

    @Column(name = "recurring_expense_id")
    private UUID recurringExpenseId;

    @Column(name = "original_amount")
    private Double originalAmount;

    @Column(name = "exchange_rate")
    private Double exchangeRate;

    @Column(name = "rate_source")
    private String rateSource;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "receipt_paths", columnDefinition = "text[]")
    private List<String> receiptPaths;

    @Column(name = "sync_status")
    private String syncStatus;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
