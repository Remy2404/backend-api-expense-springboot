package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.SavingsGoalDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.SavingsGoalEntity;
import com.wing.backendapiexpensespringboot.repository.SavingsGoalRepository;
import com.wing.backendapiexpensespringboot.dto.GoalTransactionDto;
import com.wing.backendapiexpensespringboot.model.GoalTransactionEntity;
import com.wing.backendapiexpensespringboot.repository.GoalTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavingsGoalQueryService {

        private final SavingsGoalRepository savingsGoalRepository;
        private final GoalTransactionRepository goalTransactionRepository;

        public List<SavingsGoalDto> getGoals(
                        String firebaseUid,
                        int offset,
                        int limit,
                        boolean includeArchived) {
                QueryPagination.validate(offset, limit);

                List<SavingsGoalEntity> goals = includeArchived
                                ? savingsGoalRepository.findAllByFirebaseUidOrderByCreatedAtDesc(firebaseUid)
                                : savingsGoalRepository.findActiveByFirebaseUidOrderByCreatedAtDesc(firebaseUid);

                return QueryPagination.slice(goals, offset, limit)
                                .stream()
                                .map(this::toDto)
                                .toList();
        }

        public SavingsGoalDto getGoalById(String firebaseUid, UUID id) {
                return savingsGoalRepository.findById(id)
                                .filter(goal -> goal.getFirebaseUid().equals(firebaseUid))
                                .map(this::toDto)
                                .orElseThrow(() -> AppException.notFound("Savings Goal not found"));
        }

        private SavingsGoalDto toDto(SavingsGoalEntity entity) {
                return SavingsGoalDto.builder()
                                .id(entity.getId())
                                .name(entity.getName())
                                .targetAmount(entity.getTargetAmount())
                                .currentAmount(entity.getCurrentAmount())
                                .deadline(entity.getDeadline())
                                .color(entity.getColor())
                                .icon(entity.getIcon())
                                .isArchived(entity.getIsArchived())
                                .createdAt(entity.getCreatedAt())
                                .updatedAt(entity.getUpdatedAt())
                                .build();
        }

        public List<GoalTransactionDto> getGoalTransactions(String firebaseUid, UUID goalId) {
                savingsGoalRepository.findById(goalId)
                                .filter(g -> g.getFirebaseUid().equals(firebaseUid))
                                .orElseThrow(() -> AppException.notFound("Savings Goal not found"));

                return goalTransactionRepository.findByGoalIdOrderByDateDesc(goalId)
                                .stream()
                                .map(this::toTransactionDto)
                                .toList();
        }

        private GoalTransactionDto toTransactionDto(GoalTransactionEntity entity) {
                return GoalTransactionDto.builder()
                                .id(entity.getId())
                                .goalId(entity.getGoalId())
                                .amount(entity.getAmount())
                                .type(entity.getType())
                                .date(entity.getDate())
                                .note(entity.getNote())
                                .build();
        }
}
