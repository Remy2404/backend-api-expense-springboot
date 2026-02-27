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
@Table(name = "category_budgets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBudgetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "sync_status")
    private String syncStatus;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}
