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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recurring_expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExpenseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false)
    private String firebaseUid;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "frequency", nullable = false)
    private String frequency;

    @Column(name = "currency")
    private String currency;

    @Column(name = "original_amount")
    private BigDecimal originalAmount;

    @Column(name = "exchange_rate")
    private BigDecimal exchangeRate;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "last_generated")
    private LocalDateTime lastGenerated;

    @Column(name = "next_due_date", nullable = false)
    private LocalDateTime nextDueDate;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "notification_enabled")
    private Boolean notificationEnabled;

    @Column(name = "notification_days_before")
    private Integer notificationDaysBefore;

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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
