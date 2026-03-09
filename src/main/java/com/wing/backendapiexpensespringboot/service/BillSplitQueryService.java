package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.BillSplitDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.*;
import com.wing.backendapiexpensespringboot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillSplitQueryService {

    private final BillSplitGroupRepository groupRepository;
    private final BillSplitParticipantRepository participantRepository;
    private final BillSplitExpenseRepository expenseRepository;
    private final BillSplitShareRepository shareRepository;
    private final BillSplitSettlementRepository settlementRepository;

    public List<BillSplitDto.GroupSummary> getGroupsSummary(String firebaseUid) {
        List<BillSplitGroupEntity> groups = groupRepository
                .findByCreatedByAndIsDeletedFalseOrderByCreatedAtDesc(firebaseUid);

        return groups.stream().map(group -> {
            List<BillSplitParticipantEntity> participants = participantRepository
                    .findByGroupIdOrderByCreatedAtAsc(group.getId());
            List<BillSplitExpenseEntity> expenses = expenseRepository.findByGroupIdOrderByDateDesc(group.getId());

            List<UUID> expenseIds = expenses.stream().map(BillSplitExpenseEntity::getId).toList();
            List<BillSplitShareEntity> shares = expenseIds.isEmpty() ? List.of()
                    : shareRepository.findByExpenseIdIn(expenseIds);

            Set<UUID> expenseIdSet = expenseIds.stream().collect(Collectors.toSet());
            long unsettledSharesCount = shares.stream()
                    .filter(s -> expenseIdSet.contains(s.getExpenseId()) && !Boolean.TRUE.equals(s.getIsSettled()))
                    .count();

            BigDecimal totalExpenses = expenses.stream()
                    .map(BillSplitExpenseEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return BillSplitDto.GroupSummary.builder()
                    .id(group.getId())
                    .name(group.getName())
                    .currency(group.getCurrency())
                    .createdBy(group.getCreatedBy())
                    .createdAt(group.getCreatedAt())
                    .updatedAt(group.getUpdatedAt())
                    .isDeleted(group.getIsDeleted())
                    .participantsCount(participants.size())
                    .expensesCount(expenses.size())
                    .unsettledSharesCount((int) unsettledSharesCount)
                    .totalExpenses(totalExpenses)
                    .build();
        }).toList();
    }

    public BillSplitDto.GroupDetailsPayload getGroupDetails(String firebaseUid, UUID groupId) {
        BillSplitGroupEntity group = groupRepository.findByIdAndCreatedBy(groupId, firebaseUid)
                .orElseThrow(() -> AppException.notFound("Bill Split Group not found"));

        List<BillSplitParticipantEntity> participants = participantRepository.findByGroupIdOrderByCreatedAtAsc(groupId);
        List<BillSplitExpenseEntity> expenses = expenseRepository.findByGroupIdOrderByDateDesc(groupId);
        List<BillSplitSettlementEntity> settlements = settlementRepository.findByGroupIdOrderByCreatedAtDesc(groupId);

        List<UUID> expenseIds = expenses.stream().map(BillSplitExpenseEntity::getId).toList();
        List<BillSplitShareEntity> shares = expenseIds.isEmpty() ? List.of()
                : shareRepository.findByExpenseIdIn(expenseIds);

        return BillSplitDto.GroupDetailsPayload.builder()
                .group(mapToGroupDto(group))
                .participants(participants.stream().map(this::mapToParticipantDto).toList())
                .expenses(expenses.stream().map(this::mapToExpenseDto).toList())
                .shares(shares.stream().map(this::mapToShareDto).toList())
                .settlements(settlements.stream().map(this::mapToSettlementDto).toList())
                .build();
    }

    private BillSplitDto.Group mapToGroupDto(BillSplitGroupEntity entity) {
        return BillSplitDto.Group.builder()
                .id(entity.getId())
                .name(entity.getName())
                .currency(entity.getCurrency())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .isDeleted(entity.getIsDeleted())
                .build();
    }

    private BillSplitDto.Participant mapToParticipantDto(BillSplitParticipantEntity entity) {
        return BillSplitDto.Participant.builder()
                .id(entity.getId())
                .groupId(entity.getGroupId())
                .name(entity.getName())
                .userId(entity.getUserId())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private BillSplitDto.Expense mapToExpenseDto(BillSplitExpenseEntity entity) {
        return BillSplitDto.Expense.builder()
                .id(entity.getId())
                .groupId(entity.getGroupId())
                .title(entity.getTitle())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .payerParticipantId(entity.getPayerParticipantId())
                .splitType(entity.getSplitType())
                .date(entity.getDate())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private BillSplitDto.Share mapToShareDto(BillSplitShareEntity entity) {
        return BillSplitDto.Share.builder()
                .id(entity.getId())
                .expenseId(entity.getExpenseId())
                .participantId(entity.getParticipantId())
                .amount(entity.getAmount())
                .isSettled(entity.getIsSettled())
                .settledAt(entity.getSettledAt())
                .build();
    }

    private BillSplitDto.Settlement mapToSettlementDto(BillSplitSettlementEntity entity) {
        return BillSplitDto.Settlement.builder()
                .id(entity.getId())
                .groupId(entity.getGroupId())
                .expenseId(entity.getExpenseId())
                .participantId(entity.getParticipantId())
                .amount(entity.getAmount())
                .method(entity.getMethod())
                .note(entity.getNote())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
