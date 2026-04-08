package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.SyncPushRequestDto;
import com.wing.backendapiexpensespringboot.dto.SyncPushResponseDto;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.model.GoalTransactionEntity;
import com.wing.backendapiexpensespringboot.model.SavingsGoalEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryBudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.GoalTransactionRepository;
import com.wing.backendapiexpensespringboot.repository.RecurringExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.SavingsGoalRepository;
import com.wing.backendapiexpensespringboot.service.media.ImageKitMediaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryBudgetRepository categoryBudgetRepository;

    @Mock
    private SavingsGoalRepository savingsGoalRepository;

    @Mock
    private GoalTransactionRepository goalTransactionRepository;

    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    @Mock
    private ImageKitMediaService imageKitMediaService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private DatabaseRetryExecutor databaseRetryExecutor;

    @InjectMocks
    private SyncService syncService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any());
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(databaseRetryExecutor).run(anyString(), any(Runnable.class));
    }

    @Test
    void pushCategoryKeepsPendingStatusWhenSyncedAtIsNull() {
        String firebaseUid = "firebase-user";
        UUID categoryId = UUID.randomUUID();

        CategoryEntity existing = new CategoryEntity();
        existing.setId(categoryId);
        existing.setFirebaseUid(firebaseUid);
        existing.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));

        when(categoryRepository.findAllById(any())).thenReturn(List.of(existing));
        when(categoryRepository.findActiveByFirebaseUidOrderByNameAsc(firebaseUid)).thenReturn(List.of(existing));
        when(categoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SyncPushRequestDto.CategoryItem item = new SyncPushRequestDto.CategoryItem();
        item.setId(categoryId.toString());
        item.setName("Food");
        item.setCategoryType("EXPENSE");
        item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
        item.setSyncedAt(null);

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setCategories(List.of(item));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CategoryEntity>> categoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(categoryCaptor.capture());
        CategoryEntity savedCategory = categoryCaptor.getValue().getFirst();
        assertEquals("pending", savedCategory.getSyncStatus());
        assertNull(savedCategory.getSyncedAt());
        assertEquals(1, response.getSyncedItems().getCategories());
    }

    @Test
    void pushExpensePromotesToSyncedWhenSyncedAtIsPresent() {
        String firebaseUid = "firebase-user";
        UUID expenseId = UUID.randomUUID();

        ExpenseEntity existing = new ExpenseEntity();
        existing.setId(expenseId);
        existing.setFirebaseUid(firebaseUid);
        existing.setUpdatedAt(null);

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(ExpenseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageKitMediaService.normalizeIncomingReceiptPaths(firebaseUid, List.of())).thenReturn(List.of());

        String syncedAt = "2026-03-13T09:30:00Z";

        SyncPushRequestDto.ExpenseItem item = new SyncPushRequestDto.ExpenseItem();
        item.setId(expenseId.toString());
        item.setAmount(120.5);
        item.setTransactionType("EXPENSE");
        item.setDate("2026-03-12");
        item.setUpdatedAt("2026-03-13T09:29:00Z");
        item.setSyncedAt(syncedAt);
        item.setReceiptPaths(List.of());

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setExpenses(List.of(item));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        ArgumentCaptor<ExpenseEntity> expenseCaptor = ArgumentCaptor.forClass(ExpenseEntity.class);
        verify(expenseRepository).save(expenseCaptor.capture());
        assertEquals("synced", expenseCaptor.getValue().getSyncStatus());
        assertNotNull(expenseCaptor.getValue().getSyncedAt());
        assertEquals(1, response.getSyncedItems().getExpenses());
    }

    @Test
    void pushBudgetUpsertsExistingMonthForSameUser() {
        String firebaseUid = "firebase-user";
        UUID incomingId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();

        BudgetEntity existing = new BudgetEntity();
        existing.setId(existingId);
        existing.setFirebaseUid(firebaseUid);
        existing.setMonth("2026-03");
        existing.setUpdatedAt(OffsetDateTime.parse("2026-03-14T10:00:00Z"));

        when(budgetRepository.findById(incomingId)).thenReturn(Optional.empty());
        when(budgetRepository.findByMonthAndFirebaseUid("2026-03", firebaseUid))
                .thenReturn(Optional.of(existing));
        when(budgetRepository.save(any(BudgetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SyncPushRequestDto.BudgetItem item = new SyncPushRequestDto.BudgetItem();
        item.setId(incomingId.toString());
        item.setMonth("2026-03");
        item.setTotalAmount(300.0);
        item.setUpdatedAt("2026-03-15T11:30:00Z");
        item.setCategoryBudgets(List.of());

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setBudgets(List.of(item));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        ArgumentCaptor<BudgetEntity> budgetCaptor = ArgumentCaptor.forClass(BudgetEntity.class);
        verify(budgetRepository).save(budgetCaptor.capture());
        verify(categoryBudgetRepository).deleteByBudgetId(existingId);

        BudgetEntity saved = budgetCaptor.getValue();
        assertEquals(existingId, saved.getId());
        assertEquals("2026-03", saved.getMonth());
        assertEquals(1, response.getSyncedItems().getBudgets());
        assertEquals(0, response.getFailedItems().size());
    }

    @Test
    void pushBudgetRejectsStaleUpdateAgainstExistingMonth() {
        String firebaseUid = "firebase-user";
        UUID incomingId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();

        BudgetEntity existing = new BudgetEntity();
        existing.setId(existingId);
        existing.setFirebaseUid(firebaseUid);
        existing.setMonth("2026-03");
        existing.setUpdatedAt(OffsetDateTime.parse("2026-03-15T12:00:00Z"));

        when(budgetRepository.findById(incomingId)).thenReturn(Optional.empty());
        when(budgetRepository.findByMonthAndFirebaseUid("2026-03", firebaseUid))
                .thenReturn(Optional.of(existing));

        SyncPushRequestDto.BudgetItem item = new SyncPushRequestDto.BudgetItem();
        item.setId(incomingId.toString());
        item.setMonth("2026-03");
        item.setTotalAmount(275.0);
        item.setUpdatedAt("2026-03-15T11:30:00Z");
        item.setCategoryBudgets(List.of());

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setBudgets(List.of(item));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        assertEquals(0, response.getSyncedItems().getBudgets());
        assertEquals(1, response.getFailedItems().size());
        assertEquals("Stale budget update", response.getFailedItems().get(0).getError());
    }

    @Test
    void pushGoalOnlyPersistsNewTransactionIdsAndLeavesVersionUnset() {
        String firebaseUid = "firebase-user";
        UUID goalId = UUID.randomUUID();
        UUID existingTransactionId = UUID.randomUUID();
        UUID newTransactionId = UUID.randomUUID();

        SavingsGoalEntity existingGoal = new SavingsGoalEntity();
        existingGoal.setId(goalId);
        existingGoal.setFirebaseUid(firebaseUid);
        existingGoal.setUpdatedAt(OffsetDateTime.parse("2026-03-15T09:00:00Z"));

        GoalTransactionEntity existingTransaction = new GoalTransactionEntity();
        existingTransaction.setId(existingTransactionId);
        existingTransaction.setGoalId(goalId);

        when(savingsGoalRepository.findById(goalId)).thenReturn(Optional.of(existingGoal));
        when(savingsGoalRepository.save(any(SavingsGoalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(goalTransactionRepository.findByGoalIdOrderByDateDesc(goalId)).thenReturn(List.of(existingTransaction));

        SyncPushRequestDto.GoalTransactionItem sentExistingTransaction = new SyncPushRequestDto.GoalTransactionItem();
        sentExistingTransaction.setId(existingTransactionId.toString());
        sentExistingTransaction.setAmount(15.0);
        sentExistingTransaction.setType("deposit");
        sentExistingTransaction.setDate("2026-03-15T09:30:00Z");

        SyncPushRequestDto.GoalTransactionItem duplicatedNewTransaction = new SyncPushRequestDto.GoalTransactionItem();
        duplicatedNewTransaction.setId(newTransactionId.toString());
        duplicatedNewTransaction.setAmount(30.0);
        duplicatedNewTransaction.setType("deposit");
        duplicatedNewTransaction.setNote("first");
        duplicatedNewTransaction.setDate("2026-03-15T10:00:00Z");

        SyncPushRequestDto.GoalTransactionItem latestNewTransaction = new SyncPushRequestDto.GoalTransactionItem();
        latestNewTransaction.setId(newTransactionId.toString());
        latestNewTransaction.setAmount(30.0);
        latestNewTransaction.setType("deposit");
        latestNewTransaction.setNote("latest");
        latestNewTransaction.setDate("2026-03-15T10:05:00Z");

        SyncPushRequestDto.GoalItem goalItem = new SyncPushRequestDto.GoalItem();
        goalItem.setId(goalId.toString());
        goalItem.setName("Emergency fund");
        goalItem.setTargetAmount(500.0);
        goalItem.setCurrentAmount(245.0);
        goalItem.setUpdatedAt("2026-03-15T10:10:00Z");
        goalItem.setTransactions(List.of(
                sentExistingTransaction,
                duplicatedNewTransaction,
                latestNewTransaction));

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setGoals(List.of(goalItem));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        ArgumentCaptor<GoalTransactionEntity> transactionCaptor = ArgumentCaptor.forClass(GoalTransactionEntity.class);
        verify(entityManager).persist(transactionCaptor.capture());
        verify(goalTransactionRepository, never()).save(any(GoalTransactionEntity.class));
        verify(goalTransactionRepository, never()).existsById(any());

        GoalTransactionEntity persistedTransaction = transactionCaptor.getValue();
        assertEquals(newTransactionId, persistedTransaction.getId());
        assertEquals("latest", persistedTransaction.getNote());
        assertNull(persistedTransaction.getVersion());
        assertEquals(1, response.getSyncedItems().getGoals());
        assertEquals(0, response.getFailedItems().size());
    }
}
