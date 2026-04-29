package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.DashboardSummaryResponseDto;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSummaryServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

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
        when(budgetRepository.findActiveByMonthAndFirebaseUid(any(), eq("firebase-user-1")))
                .thenReturn(Optional.of(BudgetEntity.builder().totalAmount(new BigDecimal("500.00")).build()));

        UUID foodCategoryId = UUID.randomUUID();
        UUID groceriesExpenseId = UUID.randomUUID();
        when(expenseRepository.findActiveByFirebaseUidOrderByDateDesc(eq("firebase-user-1"), any()))
                .thenReturn(List.of(
                        ExpenseEntity.builder()
                                .id(groceriesExpenseId)
                                .amount(25.0)
                                .currency("USD")
                                .transactionType("EXPENSE")
                                .categoryId(foodCategoryId)
                                .merchant("Store")
                                .note("Groceries")
                                .date(OffsetDateTime.parse("2026-03-20T10:15:30Z"))
                                .isDeleted(false)
                                .build()));
        when(categoryRepository.findActiveByFirebaseUidAndIdIn(eq("firebase-user-1"), any()))
                .thenReturn(List.of(
                        CategoryEntity.builder()
                                .id(foodCategoryId)
                                .name("Food")
                                .build()));

        DashboardSummaryResponseDto result = dashboardSummaryService.getSummary("firebase-user-1");

        assertEquals(1_000.0, result.getTotalIncome());
        assertEquals(600.0, result.getTotalExpense());
        assertEquals(400.0, result.getBalance());
        assertEquals(100L, result.getTransactionCount());
        assertEquals(250.0, result.getMonthlyIncome());
        assertEquals(100.0, result.getMonthlyExpense());
        assertEquals(500.0, result.getBudgetSummary().getBudgetLimit());
        assertEquals(100.0, result.getBudgetSummary().getSpent());
        assertEquals(400.0, result.getBudgetSummary().getRemaining());
        assertEquals(1, result.getRecentTransactions().size());
        assertEquals("Food", result.getRecentTransactions().get(0).getCategoryName());
        assertEquals(1, result.getRecentCategories().size());
        assertEquals("Food", result.getRecentCategories().get(0).getName());
    }
}
