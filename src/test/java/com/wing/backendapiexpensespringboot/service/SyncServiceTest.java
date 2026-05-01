package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.SyncPushRequestDto;
import com.wing.backendapiexpensespringboot.dto.SyncPushResponseDto;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.model.GoalTransactionEntity;
import com.wing.backendapiexpensespringboot.model.RecurringExpenseEntity;
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
import jakarta.persistence.Query;
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
import static org.mockito.Mockito.times;
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
    void pushCategoryMarksAcceptedRowSyncedWhenIncomingSyncedAtIsNull() {
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
        item.setRetryCount(7);
        item.setLastError("client retry");

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setCategories(List.of(item));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CategoryEntity>> categoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(categoryCaptor.capture());
        CategoryEntity savedCategory = categoryCaptor.getValue().getFirst();
        assertEquals("synced", savedCategory.getSyncStatus());
        assertNotNull(savedCategory.getSyncedAt());
        assertEquals(0, savedCategory.getRetryCount());
        assertNull(savedCategory.getLastError());
        assertEquals(1, response.getSyncedItems().getCategories());
    }

    @Test
    void pushDuplicateCategoryReturnsIdMapAndSavesTombstoneAsSynced() {
        String firebaseUid = "firebase-user";
        UUID canonicalId = UUID.randomUUID();
        UUID duplicateId = UUID.randomUUID();

        CategoryEntity canonical = new CategoryEntity();
        canonical.setId(canonicalId);
        canonical.setFirebaseUid(firebaseUid);
        canonical.setName("Food");
        canonical.setCategoryType("EXPENSE");

        Query query = org.mockito.Mockito.mock(Query.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(categoryRepository.findAllById(any())).thenReturn(List.of());
        when(categoryRepository.findActiveByFirebaseUidOrderByNameAsc(firebaseUid)).thenReturn(List.of(canonical));
        when(categoryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SyncPushRequestDto.CategoryItem item = new SyncPushRequestDto.CategoryItem();
        item.setId(duplicateId.toString());
        item.setName("Food");
        item.setCategoryType("EXPENSE");
        item.setUpdatedAt("2026-03-13T09:29:00Z");
        item.setDeletedAt(null);
        item.setRetryCount(4);
        item.setLastError("duplicate retry");

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setCategories(List.of(item));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CategoryEntity>> categoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(categoryRepository).saveAll(categoryCaptor.capture());
        CategoryEntity tombstone = categoryCaptor.getValue().getFirst();
        assertEquals(duplicateId, tombstone.getId());
        assertEquals(true, tombstone.getIsDeleted());
        assertEquals("synced", tombstone.getSyncStatus());
        assertNotNull(tombstone.getSyncedAt());
        assertNotNull(tombstone.getDeletedAt());
        assertEquals(0, tombstone.getRetryCount());
        assertNull(tombstone.getLastError());
        assertEquals(canonicalId.toString(), response.getCategoryIdMap().get(duplicateId.toString()));
        verify(query, times(3)).executeUpdate();
    }

    @Test
    void pushExpenseMarksAcceptedRowSyncedWhenIncomingSyncedAtIsNull() {
        String firebaseUid = "firebase-user";
        UUID expenseId = UUID.randomUUID();

        ExpenseEntity existing = new ExpenseEntity();
        existing.setId(expenseId);
        existing.setFirebaseUid(firebaseUid);
        existing.setUpdatedAt(null);

        when(expenseRepository.findById(expenseId)).thenReturn(Optional.of(existing));
        when(expenseRepository.save(any(ExpenseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageKitMediaService.normalizeIncomingReceiptPaths(firebaseUid, List.of())).thenReturn(List.of());

        SyncPushRequestDto.ExpenseItem item = new SyncPushRequestDto.ExpenseItem();
        item.setId(expenseId.toString());
        item.setAmount(120.5);
        item.setTransactionType("EXPENSE");
        item.setDate("2026-03-12");
        item.setUpdatedAt("2026-03-13T09:29:00Z");
        item.setSyncedAt(null);
        item.setReceiptPaths(List.of());
        item.setRetryCount(3);
        item.setLastError("client retry");

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setExpenses(List.of(item));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        ArgumentCaptor<ExpenseEntity> expenseCaptor = ArgumentCaptor.forClass(ExpenseEntity.class);
        verify(expenseRepository).save(expenseCaptor.capture());
        assertEquals("synced", expenseCaptor.getValue().getSyncStatus());
        assertNotNull(expenseCaptor.getValue().getSyncedAt());
        assertEquals(0, expenseCaptor.getValue().getRetryCount());
        assertNull(expenseCaptor.getValue().getLastError());
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
        item.setSyncedAt(null);
        item.setRetryCount(6);
        item.setLastError("client retry");
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
        assertEquals("synced", saved.getSyncStatus());
        assertNotNull(saved.getSyncedAt());
        assertEquals(0, saved.getRetryCount());
        assertNull(saved.getLastError());
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
        verify(budgetRepository, never()).save(any(BudgetEntity.class));
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
        goalItem.setSyncedAt(null);
        goalItem.setRetryCount(2);
        goalItem.setLastError("client retry");
        goalItem.setTransactions(List.of(
                sentExistingTransaction,
                duplicatedNewTransaction,
                latestNewTransaction));

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setGoals(List.of(goalItem));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        ArgumentCaptor<SavingsGoalEntity> goalCaptor = ArgumentCaptor.forClass(SavingsGoalEntity.class);
        verify(savingsGoalRepository).save(goalCaptor.capture());
        SavingsGoalEntity savedGoal = goalCaptor.getValue();
        assertEquals("synced", savedGoal.getSyncStatus());
        assertNotNull(savedGoal.getSyncedAt());
        assertEquals(0, savedGoal.getRetryCount());
        assertNull(savedGoal.getLastError());

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

    @Test
    void pushRecurringMarksAcceptedRowSyncedWhenIncomingSyncedAtIsNull() {
        String firebaseUid = "firebase-user";
        UUID recurringId = UUID.randomUUID();

        RecurringExpenseEntity existing = new RecurringExpenseEntity();
        existing.setId(recurringId);
        existing.setFirebaseUid(firebaseUid);
        existing.setUpdatedAt(OffsetDateTime.parse("2026-03-15T09:00:00Z"));

        when(recurringExpenseRepository.findById(recurringId)).thenReturn(Optional.of(existing));
        when(recurringExpenseRepository.save(any(RecurringExpenseEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SyncPushRequestDto.RecurringItem item = new SyncPushRequestDto.RecurringItem();
        item.setId(recurringId.toString());
        item.setAmount(15.0);
        item.setFrequency("monthly");
        item.setStartDate("2026-03-15T09:00:00Z");
        item.setNextDueDate("2026-04-15T09:00:00Z");
        item.setUpdatedAt("2026-03-15T10:00:00Z");
        item.setSyncedAt(null);
        item.setRetryCount(5);
        item.setLastError("client retry");

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setRecurring(List.of(item));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        ArgumentCaptor<RecurringExpenseEntity> recurringCaptor = ArgumentCaptor.forClass(RecurringExpenseEntity.class);
        verify(recurringExpenseRepository).save(recurringCaptor.capture());
        RecurringExpenseEntity saved = recurringCaptor.getValue();
        assertEquals("synced", saved.getSyncStatus());
        assertNotNull(saved.getSyncedAt());
        assertEquals(0, saved.getRetryCount());
        assertNull(saved.getLastError());
        assertEquals(1, response.getSyncedItems().getRecurring());
    }
}
