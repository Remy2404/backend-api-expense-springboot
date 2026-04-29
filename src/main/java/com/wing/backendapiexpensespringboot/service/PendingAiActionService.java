package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.dto.agent.*;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.PendingAiActionEntity;
import com.wing.backendapiexpensespringboot.repository.PendingAiActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingAiActionService {

    private static final long EXPIRATION_MINUTES = 15;

    private final PendingAiActionRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UUID store(String firebaseUid, AgentActionType actionType, AgentProposal proposal) {
        try {
            String json = objectMapper.writeValueAsString(proposal);
            PendingAiActionEntity entity = PendingAiActionEntity.builder()
                    .firebaseUid(firebaseUid)
                    .actionType(actionType.name())
                    .proposalJson(json)
                    .status("PENDING")
                    .expiresAt(Instant.now().plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES))
                    .build();
            PendingAiActionEntity saved = repository.save(entity);
            log.info("Stored pending action {} for user {}", saved.getId(), firebaseUid);
            return saved.getId();
        } catch (Exception e) {
            log.error("Failed to store pending action for user {}", firebaseUid, e);
            throw AppException.internalError("Failed to store pending action");
        }
    }

    @Transactional
    public AgentProposal confirm(String firebaseUid, UUID actionId) {
        PendingAiActionEntity entity = repository
                .findByIdAndFirebaseUidAndStatus(actionId, firebaseUid, "PENDING")
                .orElseThrow(() -> AppException.notFound("Pending action not found or expired"));

        if (entity.getExpiresAt().isBefore(Instant.now())) {
            entity.setStatus("EXPIRED");
            repository.save(entity);
            throw AppException.badRequest("This action has expired. Please try again.");
        }

        entity.setStatus("CONFIRMED");
        repository.save(entity);

        AgentActionType actionType = AgentActionType.valueOf(entity.getActionType());
        return deserializeProposal(actionType, entity.getProposalJson());
    }

    @Transactional
    public void cancel(String firebaseUid, UUID actionId) {
        PendingAiActionEntity entity = repository
                .findByIdAndFirebaseUidAndStatus(actionId, firebaseUid, "PENDING")
                .orElseThrow(() -> AppException.notFound("Pending action not found or already resolved"));

        entity.setStatus("CANCELLED");
        repository.save(entity);
        log.info("Cancelled pending action {} for user {}", actionId, firebaseUid);
    }

    private AgentProposal deserializeProposal(AgentActionType actionType, String json) {
        try {
            return switch (actionType) {
                case PREPARE_TRANSACTION -> objectMapper.readValue(json, TransactionProposal.class);
                case PREPARE_BUDGET -> objectMapper.readValue(json, BudgetProposal.class);
                case PREPARE_GOAL -> objectMapper.readValue(json, GoalProposal.class);
                case PREPARE_CATEGORY -> objectMapper.readValue(json, CategoryProposal.class);
                case PREPARE_RECURRING_EXPENSE -> objectMapper.readValue(json, RecurringExpenseProposal.class);
                default -> throw AppException.internalError("Invalid action type for proposal: " + actionType);
            };
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to deserialize proposal JSON", e);
            throw AppException.internalError("Failed to read pending action data");
        }
    }
}
