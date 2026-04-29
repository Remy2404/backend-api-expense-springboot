package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.agent.TransactionProposal;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.CategoryType;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.RecurringExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.SavingsGoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPendingActionExecutionServiceTest {

    @Mock
    private PendingAiActionService pendingAiActionService;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private SavingsGoalRepository savingsGoalRepository;

    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    @InjectMocks
    private AiPendingActionExecutionService service;

    private static final String FIREBASE_UID = "firebase-user-1";

    private CategoryEntity groceriesCategory;

    @BeforeEach
    void setUp() {
        groceriesCategory = CategoryEntity.builder()
                .id(UUID.randomUUID())
                .firebaseUid(FIREBASE_UID)
                .name("Groceries")
                .categoryType("EXPENSE")
                .icon("shopping-cart")
                .color("#22C55E")
                .build();
    }

    @Test
    void confirmAndExecuteCreatesExpenseForPreparedTransaction() {
        UUID actionId = UUID.randomUUID();
        TransactionProposal proposal = new TransactionProposal(List.of(
                new TransactionProposal.TransactionItem(
                        "expense",
                        45.50,
                        "USD",
                        "Groceries",
                        "Whole Foods",
                        "Whole Foods groceries",
                        "2026-04-29",
                        "Whole Foods")));

        when(pendingAiActionService.confirm(FIREBASE_UID, actionId)).thenReturn(proposal);
        when(categoryService.getCategoriesByType(FIREBASE_UID, CategoryType.EXPENSE))
                .thenReturn(List.of(groceriesCategory));

        service.confirmAndExecute(FIREBASE_UID, actionId);

        ArgumentCaptor<Map<String, Object>> expenseCaptor = ArgumentCaptor.forClass(Map.class);
        verify(expenseService).createExpense(eq(FIREBASE_UID), expenseCaptor.capture());
        Map<String, Object> capturedExpense = expenseCaptor.getValue();
        assertThat(capturedExpense.get("amount")).isEqualTo(45.50);
        assertThat(capturedExpense.get("transactionType")).isEqualTo("EXPENSE");
        assertThat(capturedExpense.get("categoryId")).isEqualTo(groceriesCategory.getId());
        assertThat(capturedExpense.get("merchant")).isEqualTo("Whole Foods");
    }

    @Test
    void confirmAndExecuteRejectsTransactionWithoutAmount() {
        UUID actionId = UUID.randomUUID();
        TransactionProposal proposal = new TransactionProposal(List.of(
                new TransactionProposal.TransactionItem(
                        "expense",
                        null,
                        "USD",
                        "Groceries",
                        "Whole Foods",
                        null,
                        "2026-04-29",
                        "Whole Foods")));

        when(pendingAiActionService.confirm(FIREBASE_UID, actionId)).thenReturn(proposal);

        assertThatThrownBy(() -> service.confirmAndExecute(FIREBASE_UID, actionId))
                .isInstanceOf(AppException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(expenseService, never()).createExpense(eq(FIREBASE_UID), anyMap());
    }

    @Test
    void confirmAndExecuteUpsertsBudgetByMonth() {
        UUID actionId = UUID.randomUUID();
        BudgetEntity existingBudget = BudgetEntity.builder()
                .id(UUID.randomUUID())
                .firebaseUid(FIREBASE_UID)
                .month("2026-04")
                .build();

        when(pendingAiActionService.confirm(FIREBASE_UID, actionId))
                .thenReturn(new com.wing.backendapiexpensespringboot.dto.agent.BudgetProposal("2026-04", 400.0));
        when(budgetRepository.findByMonthAndFirebaseUid("2026-04", FIREBASE_UID))
                .thenReturn(Optional.of(existingBudget));

        service.confirmAndExecute(FIREBASE_UID, actionId);

        verify(budgetRepository).save(existingBudget);
        assertThat(existingBudget.getTotalAmount()).isNotNull();
        assertThat(existingBudget.getTotalAmount().doubleValue()).isEqualTo(400.0);
    }

    @Test
    void cancelDelegatesToPendingActionService() {
        UUID actionId = UUID.randomUUID();

        service.cancel(FIREBASE_UID, actionId);

        verify(pendingAiActionService).cancel(FIREBASE_UID, actionId);
    }
}
