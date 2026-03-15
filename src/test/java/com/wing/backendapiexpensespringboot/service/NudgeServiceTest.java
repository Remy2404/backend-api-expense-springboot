package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.NudgesResponse;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryBudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryBudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.RecurringExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.SavingsGoalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NudgeServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private CategoryBudgetRepository categoryBudgetRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;
    @Mock
    private SavingsGoalRepository savingsGoalRepository;

    @InjectMocks
    private NudgeService nudgeService;

    @Test
    void getNudges_returnsBudgetExceededInsightWithActions() {
        String firebaseUid = "firebase-user";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        UUID budgetId = UUID.randomUUID();
        UUID foodCategoryId = UUID.randomUUID();

        ExpenseEntity foodExpense = expense(
                firebaseUid,
                180.0,
                "EXPENSE",
                now.minusDays(2),
                foodCategoryId,
                "Whole Foods",
                null
        );

        when(categoryRepository.findActiveByFirebaseUidOrderByNameAsc(firebaseUid)).thenReturn(List.of(
                CategoryEntity.builder()
                        .id(foodCategoryId)
                        .firebaseUid(firebaseUid)
                        .name("Food")
                        .categoryType("expense")
                        .icon("utensils")
                        .color("#22c55e")
                        .build()
        ));
        when(expenseRepository.findByFirebaseUidAndDateBetweenOrderByDateDesc(eq(firebaseUid), any(), any()))
                .thenAnswer((invocation) -> filterExpensesBetween(
                        List.of(foodExpense),
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));
        when(budgetRepository.findActiveByMonthAndFirebaseUid(now.toLocalDate().withDayOfMonth(1).toString().substring(0, 7), firebaseUid))
                .thenReturn(Optional.of(BudgetEntity.builder()
                        .id(budgetId)
                        .firebaseUid(firebaseUid)
                        .month(now.toLocalDate().withDayOfMonth(1).toString().substring(0, 7))
                        .totalAmount(BigDecimal.valueOf(300.0))
                        .build()));
        when(categoryBudgetRepository.findByBudgetIdAndFirebaseUid(budgetId, firebaseUid)).thenReturn(List.of(
                CategoryBudgetEntity.builder()
                        .budgetId(budgetId)
                        .categoryId(foodCategoryId)
                        .amount(BigDecimal.valueOf(120.0))
                        .build()
        ));
        when(recurringExpenseRepository.findActiveByFirebaseUidOrderByNextDueDateAsc(firebaseUid)).thenReturn(List.of());
        when(savingsGoalRepository.findActiveByFirebaseUidOrderByCreatedAtDesc(firebaseUid)).thenReturn(List.of());

        NudgesResponse response = nudgeService.getNudges(firebaseUid);

        assertThat(response.getGeneratedAt()).isNotNull();
        assertThat(response.getNudges()).isNotEmpty();
        assertThat(response.getNudges().get(0).getType()).isEqualTo("budget_exceeded");
        assertThat(response.getNudges().get(0).getBody()).contains("Food budget");
        assertThat(response.getNudges().get(0).getActions())
                .extracting("action")
                .contains("edit_budget", "view_transactions");
        assertThat(response.getNudges().get(0).getGeneratedAt()).isNotNull();
    }

    @Test
    void getNudges_detectsRecurringSubscriptionCandidate() {
        String firebaseUid = "firebase-user";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

        ExpenseEntity januaryCharge = expense(
                firebaseUid,
                12.0,
                "EXPENSE",
                now.minusDays(62),
                null,
                "Netflix",
                null
        );
        ExpenseEntity februaryCharge = expense(
                firebaseUid,
                12.0,
                "EXPENSE",
                now.minusDays(31),
                null,
                "Netflix",
                null
        );
        ExpenseEntity marchCharge = expense(
                firebaseUid,
                12.0,
                "EXPENSE",
                now.minusDays(2),
                null,
                "Netflix",
                null
        );

        List<ExpenseEntity> expenses = List.of(januaryCharge, februaryCharge, marchCharge);

        when(categoryRepository.findActiveByFirebaseUidOrderByNameAsc(firebaseUid)).thenReturn(List.of());
        when(expenseRepository.findByFirebaseUidAndDateBetweenOrderByDateDesc(eq(firebaseUid), any(), any()))
                .thenAnswer((invocation) -> filterExpensesBetween(
                        expenses,
                        invocation.getArgument(1),
                        invocation.getArgument(2)
                ));
        when(budgetRepository.findActiveByMonthAndFirebaseUid(any(), eq(firebaseUid))).thenReturn(Optional.empty());
        when(recurringExpenseRepository.findActiveByFirebaseUidOrderByNextDueDateAsc(firebaseUid)).thenReturn(List.of());
        when(savingsGoalRepository.findActiveByFirebaseUidOrderByCreatedAtDesc(firebaseUid)).thenReturn(List.of());

        NudgesResponse response = nudgeService.getNudges(firebaseUid);

        assertThat(response.getNudges())
                .extracting("type")
                .contains("recurring_subscription");
        assertThat(response.getNudges().stream()
                .filter((item) -> "recurring_subscription".equals(item.getType()))
                .findFirst())
                .get()
                .satisfies((item) -> {
                    assertThat(item.getBody()).contains("Netflix");
                    assertThat(item.getActions())
                            .extracting("action")
                            .contains("create_recurring_expense", "ignore_suggestion");
                });
    }

    private List<ExpenseEntity> filterExpensesBetween(
            List<ExpenseEntity> expenses,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    ) {
        return expenses.stream()
                .filter((expense) -> expense.getDate() != null)
                .filter((expense) -> !expense.getDate().isBefore(startInclusive))
                .filter((expense) -> expense.getDate().isBefore(endExclusive))
                .toList();
    }

    private ExpenseEntity expense(
            String firebaseUid,
            double amount,
            String transactionType,
            OffsetDateTime date,
            UUID categoryId,
            String merchant,
            UUID recurringExpenseId
    ) {
        return ExpenseEntity.builder()
                .id(UUID.randomUUID())
                .firebaseUid(firebaseUid)
                .amount(amount)
                .transactionType(transactionType)
                .currency("USD")
                .date(date)
                .categoryId(categoryId)
                .merchant(merchant)
                .recurringExpenseId(recurringExpenseId)
                .isDeleted(false)
                .createdAt(date)
                .updatedAt(date)
                .build();
    }
}
