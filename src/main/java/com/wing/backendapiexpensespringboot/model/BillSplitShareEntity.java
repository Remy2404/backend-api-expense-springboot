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
@Table(name = "bill_split_shares")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillSplitShareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "is_settled", nullable = false)
    private Boolean isSettled;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}
