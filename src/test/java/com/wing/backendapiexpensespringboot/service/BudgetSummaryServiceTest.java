package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.BudgetSummaryResponseDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetSummaryServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseRepository.FinanceSummaryAggregate monthAggregate;

    @InjectMocks
    private BudgetSummaryService budgetSummaryService;

    @Test
    void getBudgetSummaryReturnsBudgetLimitAndSpentForMonth() {
        BudgetEntity marchBudget = BudgetEntity.builder()
                .totalAmount(new BigDecimal("500.00"))
                .build();
        when(budgetRepository.findActiveByMonthAndFirebaseUid("2026-03", "firebase-user-1"))
                .thenReturn(Optional.of(marchBudget));
        when(monthAggregate.getTotalExpense()).thenReturn(320.0);
        when(expenseRepository.summarizeByFirebaseUidAndDateBetween(eq("firebase-user-1"), any(), any()))
                .thenReturn(monthAggregate);

        BudgetSummaryResponseDto result = budgetSummaryService.getBudgetSummary("firebase-user-1", "2026-03");

        assertEquals(500.0, result.getBudgetLimit());
        assertEquals(320.0, result.getSpent());
        assertEquals(180.0, result.getRemaining());
    }

    @Test
    void getBudgetSummaryReturnsZeroLimitWhenNoBudgetExists() {
        when(budgetRepository.findActiveByMonthAndFirebaseUid("2026-03", "firebase-user-1"))
                .thenReturn(Optional.empty());
        when(expenseRepository.summarizeByFirebaseUidAndDateBetween(eq("firebase-user-1"), any(), any()))
                .thenReturn(monthAggregate);
        when(monthAggregate.getTotalExpense()).thenReturn(75.0);

        BudgetSummaryResponseDto result = budgetSummaryService.getBudgetSummary("firebase-user-1", "2026-03");

        assertEquals(0.0, result.getBudgetLimit());
        assertEquals(75.0, result.getSpent());
        assertEquals(-75.0, result.getRemaining());
    }

    @Test
    void getBudgetSummaryRejectsInvalidMonthFormat() {
        assertThrows(AppException.class, () -> budgetSummaryService.getBudgetSummary("firebase-user-1", "2026/03"));
    }
}
