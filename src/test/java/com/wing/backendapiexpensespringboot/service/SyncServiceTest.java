package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.SyncPushRequestDto;
import com.wing.backendapiexpensespringboot.dto.SyncPushResponseDto;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryBudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.GoalTransactionRepository;
import com.wing.backendapiexpensespringboot.repository.RecurringExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.SavingsGoalRepository;
import com.wing.backendapiexpensespringboot.service.media.ImageKitMediaService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private SyncService syncService;

    @Test
    void pushCategoryKeepsPendingStatusWhenSyncedAtIsNull() {
        String firebaseUid = "firebase-user";
        UUID categoryId = UUID.randomUUID();

        CategoryEntity existing = new CategoryEntity();
        existing.setId(categoryId);
        existing.setFirebaseUid(firebaseUid);
        existing.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SyncPushRequestDto.CategoryItem item = new SyncPushRequestDto.CategoryItem();
        item.setId(categoryId.toString());
        item.setName("Food");
        item.setCategoryType("EXPENSE");
        item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
        item.setSyncedAt(null);

        SyncPushRequestDto request = new SyncPushRequestDto();
        request.setCategories(List.of(item));

        SyncPushResponseDto response = syncService.push(firebaseUid, request);

        ArgumentCaptor<CategoryEntity> categoryCaptor = ArgumentCaptor.forClass(CategoryEntity.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertEquals("pending", categoryCaptor.getValue().getSyncStatus());
        assertNull(categoryCaptor.getValue().getSyncedAt());
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
}
