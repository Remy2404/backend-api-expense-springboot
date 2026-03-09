package com.wing.backendapiexpensespringboot.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class BillSplitDto {

    @Builder
    public record Group(
            UUID id,
            String name,
            String currency,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Boolean isDeleted) {
    }

    @Builder
    public record Participant(
            UUID id,
            UUID groupId,
            String name,
            String userId,
            LocalDateTime createdAt) {
    }

    @Builder
    public record Expense(
            UUID id,
            UUID groupId,
            String title,
            BigDecimal amount,
            String currency,
            UUID payerParticipantId,
            String splitType,
            LocalDateTime date,
            String notes,
            LocalDateTime createdAt) {
    }

    @Builder
    public record Share(
            UUID id,
            UUID expenseId,
            UUID participantId,
            BigDecimal amount,
            Boolean isSettled,
            LocalDateTime settledAt) {
    }

    @Builder
    public record Settlement(
            UUID id,
            UUID groupId,
            UUID expenseId,
            UUID participantId,
            BigDecimal amount,
            String method,
            String note,
            LocalDateTime createdAt) {
    }

    @Builder
    public record GroupSummary(
            UUID id,
            String name,
            String currency,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Boolean isDeleted,
            int participantsCount,
            int expensesCount,
            int unsettledSharesCount,
            BigDecimal totalExpenses) {
    }

    @Builder
    public record GroupDetailsPayload(
            Group group,
            List<Participant> participants,
            List<Expense> expenses,
            List<Share> shares,
            List<Settlement> settlements) {
    }

    public record CreateGroupRequest(
            String name,
            String currency,
            List<String> participantNames) {
    }

    public record AddExpenseRequest(
            String title,
            BigDecimal amount,
            String currency,
            UUID payerParticipantId,
            String notes,
            LocalDateTime date,
            String splitType,
            List<UUID> participantIds,
            java.util.Map<UUID, BigDecimal> customShares) {
    }

    public record SettleShareRequest(
            UUID shareId,
            UUID expenseId,
            UUID participantId,
            BigDecimal amount,
            String method,
            String note) {
    }
}
