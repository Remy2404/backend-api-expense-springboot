package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceSummaryServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseRepository.FinanceSummaryAggregate aggregate;

    @InjectMocks
    private FinanceSummaryService financeSummaryService;

    @Test
    void getSummaryUsesAllTimeAggregateWithoutNullableDateParameters() {
        when(expenseRepository.summarizeByFirebaseUid("firebase-user-1")).thenReturn(aggregate);

        financeSummaryService.getSummary("firebase-user-1", "all-time");

        verify(expenseRepository).summarizeByFirebaseUid("firebase-user-1");
        verify(expenseRepository, never()).summarizeByFirebaseUidAndDateBetween(eq("firebase-user-1"), any(), any());
    }

    @Test
    void getSummaryUsesBoundedAggregateForThisMonth() {
        when(expenseRepository.summarizeByFirebaseUidAndDateBetween(eq("firebase-user-1"), any(), any()))
                .thenReturn(aggregate);

        financeSummaryService.getSummary("firebase-user-1", "this-month");

        verify(expenseRepository, never()).summarizeByFirebaseUid("firebase-user-1");
        verify(expenseRepository).summarizeByFirebaseUidAndDateBetween(eq("firebase-user-1"), any(), any());
    }
}
