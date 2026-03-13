package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ExpenseMutationRequestDto;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import com.wing.backendapiexpensespringboot.service.media.ImageKitMediaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ImageKitMediaService imageKitMediaService;

    @InjectMocks
    private ExpenseService expenseService;

    @Test
    void createExpenseReturnsExistingRowWhenConcurrentClientIdRaceOccurs() {
        String firebaseUid = "firebase-user-1";
        UUID clientId = UUID.randomUUID();
        ExpenseMutationRequestDto request = new ExpenseMutationRequestDto();
        request.setClientId(clientId.toString());
        request.setClientCreatedAt("2026-03-03T11:52:00Z");
        request.setAmount(12.5);
        request.setTransactionType("EXPENSE");
        request.setDate("2026-03-03");

        ExpenseEntity existing = ExpenseEntity.builder()
                .id(clientId)
                .firebaseUid(firebaseUid)
                .amount(12.5)
                .transactionType("EXPENSE")
                .date(OffsetDateTime.of(2026, 3, 3, 0, 0, 0, 0, ZoneOffset.UTC))
                .createdAt(OffsetDateTime.of(2026, 3, 3, 11, 52, 0, 0, ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.of(2026, 3, 3, 11, 52, 0, 0, ZoneOffset.UTC))
                .build();

        when(expenseRepository.findById(clientId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(ExpenseEntity.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(ExpenseEntity.class, clientId));

        ExpenseEntity result = expenseService.createExpense(firebaseUid, request);

        assertEquals(clientId, result.getId());
        assertEquals(firebaseUid, result.getFirebaseUid());
        assertEquals(12.5, result.getAmount());
    }
}
