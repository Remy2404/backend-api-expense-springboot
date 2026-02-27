package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.RecurringExpenseDto;
import com.wing.backendapiexpensespringboot.model.RecurringExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.RecurringExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecurringExpenseQueryService {

    private final RecurringExpenseRepository recurringExpenseRepository;

    public List<RecurringExpenseDto> getRecurringExpenses(String firebaseUid) {
        return recurringExpenseRepository.findActiveByFirebaseUidOrderByNextDueDateAsc(firebaseUid)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private RecurringExpenseDto toDto(RecurringExpenseEntity entity) {
        return RecurringExpenseDto.builder()
                .id(entity.getId())
                .amount(entity.getAmount())
                .categoryId(entity.getCategoryId())
                .notes(entity.getNotes())
                .frequency(entity.getFrequency())
                .currency(entity.getCurrency())
                .originalAmount(entity.getOriginalAmount())
                .exchangeRate(entity.getExchangeRate())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .lastGenerated(entity.getLastGenerated())
                .nextDueDate(entity.getNextDueDate())
                .isActive(entity.getIsActive())
                .notificationEnabled(entity.getNotificationEnabled())
                .notificationDaysBefore(entity.getNotificationDaysBefore())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
