package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.RecurringExpenseDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.RecurringExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.RecurringExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecurringExpenseQueryService {

        private final RecurringExpenseRepository recurringExpenseRepository;

        public List<RecurringExpenseDto> getRecurringExpenses(
                        String firebaseUid,
                        int offset,
                        int limit,
                        boolean includeArchived) {
                QueryPagination.validate(offset, limit);

                List<RecurringExpenseEntity> expenses = includeArchived
                                ? recurringExpenseRepository.findAllByFirebaseUidOrderByNextDueDateAsc(firebaseUid)
                                : recurringExpenseRepository.findActiveByFirebaseUidOrderByNextDueDateAsc(firebaseUid);

                return QueryPagination.slice(expenses, offset, limit)
                                .stream()
                                .map(this::toDto)
                                .toList();
        }

        public RecurringExpenseDto getRecurringExpenseById(String firebaseUid, UUID id) {
                return recurringExpenseRepository.findById(id)
                                .filter(expense -> expense.getFirebaseUid().equals(firebaseUid))
                                .map(this::toDto)
                                .orElseThrow(() -> AppException.notFound("Recurring Expense not found"));
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
