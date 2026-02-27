package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.SavingsGoalDto;
import com.wing.backendapiexpensespringboot.model.SavingsGoalEntity;
import com.wing.backendapiexpensespringboot.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SavingsGoalQueryService {

    private final SavingsGoalRepository savingsGoalRepository;

    public List<SavingsGoalDto> getGoals(
            String firebaseUid,
            int offset,
            int limit,
            boolean includeArchived
    ) {
        QueryPagination.validate(offset, limit);

        List<SavingsGoalEntity> goals = includeArchived
                ? savingsGoalRepository.findAllByFirebaseUidOrderByCreatedAtDesc(firebaseUid)
                : savingsGoalRepository.findActiveByFirebaseUidOrderByCreatedAtDesc(firebaseUid);

        return QueryPagination.slice(goals, offset, limit)
                .stream()
                .map(this::toDto)
                .toList();
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
}
