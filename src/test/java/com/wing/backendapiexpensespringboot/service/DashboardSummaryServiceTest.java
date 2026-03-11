package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.DashboardSummaryResponseDto;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSummaryServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseRepository.FinanceSummaryAggregate allTimeAggregate;

    @Mock
    private ExpenseRepository.FinanceSummaryAggregate monthAggregate;

    @InjectMocks
    private DashboardSummaryService dashboardSummaryService;

    @Test
    void getSummaryUsesAllTransactionsAndCurrentMonthAggregate() {
        when(allTimeAggregate.getTotalIncome()).thenReturn(1_000.0);
        when(allTimeAggregate.getTotalExpense()).thenReturn(600.0);
        when(allTimeAggregate.getTransactionCount()).thenReturn(100L);
        when(monthAggregate.getTotalIncome()).thenReturn(250.0);
        when(monthAggregate.getTotalExpense()).thenReturn(100.0);

        when(expenseRepository.summarizeByFirebaseUid(eq("firebase-user-1"))).thenReturn(allTimeAggregate);
        when(expenseRepository.summarizeByFirebaseUidAndDateBetween(eq("firebase-user-1"), any(), any()))
                .thenReturn(monthAggregate);

        DashboardSummaryResponseDto result = dashboardSummaryService.getSummary("firebase-user-1");

        assertEquals(1_000.0, result.getTotalIncome());
        assertEquals(600.0, result.getTotalExpense());
        assertEquals(400.0, result.getBalance());
        assertEquals(100L, result.getTransactionCount());
        assertEquals(250.0, result.getMonthlyIncome());
        assertEquals(100.0, result.getMonthlyExpense());
    }
}
