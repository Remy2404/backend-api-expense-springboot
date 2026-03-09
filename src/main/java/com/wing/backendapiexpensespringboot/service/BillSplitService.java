package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.BillSplitDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.*;
import com.wing.backendapiexpensespringboot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillSplitService {

    private final BillSplitGroupRepository groupRepository;
    private final BillSplitParticipantRepository participantRepository;
    private final BillSplitExpenseRepository expenseRepository;
    private final BillSplitShareRepository shareRepository;
    private final BillSplitSettlementRepository settlementRepository;

    @Transactional
    public BillSplitGroupEntity createGroup(String firebaseUid, BillSplitDto.CreateGroupRequest request) {
        BillSplitGroupEntity group = BillSplitGroupEntity.builder()
                .name(request.name())
                .currency(request.currency())
                .createdBy(firebaseUid)
                .isDeleted(false)
                .build();
        group = groupRepository.save(group);

        List<BillSplitParticipantEntity> participants = new ArrayList<>();
        for (String name : request.participantNames()) {
            participants.add(BillSplitParticipantEntity.builder()
                    .groupId(group.getId())
                    .name(name)
                    .userId("you".equalsIgnoreCase(name) ? firebaseUid : null)
                    .build());
        }
        participantRepository.saveAll(participants);

        return group;
    }

    @Transactional
    public BillSplitExpenseEntity addExpense(String firebaseUid, UUID groupId, BillSplitDto.AddExpenseRequest request) {
        // Verify group exists and belongs to user
        groupRepository.findByIdAndCreatedBy(groupId, firebaseUid)
                .orElseThrow(() -> AppException.notFound("Bill Split Group not found or unauthorized"));

        BillSplitExpenseEntity expense = BillSplitExpenseEntity.builder()
                .groupId(groupId)
                .title(request.title())
                .amount(request.amount())
                .currency(request.currency())
                .payerParticipantId(request.payerParticipantId())
                .splitType(request.splitType())
                .date(request.date() != null ? request.date() : LocalDateTime.now())
                .notes(request.notes())
                .build();
        expense = expenseRepository.save(expense);

        List<BillSplitShareEntity> shares = new ArrayList<>();
        if ("custom".equals(request.splitType()) && request.customShares() != null) {
            for (Map.Entry<UUID, BigDecimal> entry : request.customShares().entrySet()) {
                UUID participantId = entry.getKey();
                BigDecimal amount = entry.getValue();
                boolean isSettled = participantId.equals(request.payerParticipantId());
                shares.add(BillSplitShareEntity.builder()
                        .expenseId(expense.getId())
                        .participantId(participantId)
                        .amount(amount)
                        .isSettled(isSettled)
                        .build());
            }
        } else {
            // Equal split
            List<UUID> participantIds = request.participantIds();
            if (participantIds == null || participantIds.isEmpty()) {
                throw AppException.badRequest("Please add at least one participant to split this expense.");
            }
            int count = participantIds.size();
            BigDecimal amountPerPerson = request.amount().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            BigDecimal totalDistributed = amountPerPerson.multiply(BigDecimal.valueOf(count));
            BigDecimal remainder = request.amount().subtract(totalDistributed);

            for (int i = 0; i < count; i++) {
                UUID participantId = participantIds.get(i);
                BigDecimal amount = amountPerPerson;
                if (i == 0) {
                    amount = amount.add(remainder);
                }
                boolean isSettled = participantId.equals(request.payerParticipantId());
                shares.add(BillSplitShareEntity.builder()
                        .expenseId(expense.getId())
                        .participantId(participantId)
                        .amount(amount)
                        .isSettled(isSettled)
                        .build());
            }
        }

        shareRepository.saveAll(shares);

        return expense;
    }

    @Transactional
    public void settleShare(String firebaseUid, UUID groupId, BillSplitDto.SettleShareRequest request) {
        // Verify group exists and belongs to user
        groupRepository.findByIdAndCreatedBy(groupId, firebaseUid)
                .orElseThrow(() -> AppException.notFound("Bill Split Group not found or unauthorized"));

        BillSplitShareEntity share = shareRepository.findById(request.shareId())
                .orElseThrow(() -> AppException.notFound("Share not found"));

        if (Boolean.TRUE.equals(share.getIsSettled())) {
            throw AppException.badRequest("Share is already settled");
        }

        share.setIsSettled(true);
        share.setSettledAt(LocalDateTime.now());
        shareRepository.save(share);

        BillSplitSettlementEntity settlement = BillSplitSettlementEntity.builder()
                .groupId(groupId)
                .expenseId(request.expenseId())
                .participantId(request.participantId())
                .amount(request.amount())
                .method(request.method())
                .note(request.note())
                .build();
        settlementRepository.save(settlement);
    }
}
