package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.SyncPullResponseDto;
import com.wing.backendapiexpensespringboot.dto.SyncPushRequestDto;
import com.wing.backendapiexpensespringboot.dto.SyncPushResponseDto;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryBudgetEntity;
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
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final GoalTransactionRepository goalTransactionRepository;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final EntityManager entityManager;

    @Transactional
    public SyncPushResponseDto push(String firebaseUid, SyncPushRequestDto request) {
        SyncPushResponseDto response = SyncPushResponseDto.empty();

        syncCategories(firebaseUid, safeList(request.getCategories()), response);
        syncExpenses(firebaseUid, safeList(request.getExpenses()), response);
        syncBudgets(firebaseUid, safeList(request.getBudgets()), response);
        syncGoals(firebaseUid, safeList(request.getGoals()), response);
        syncRecurring(firebaseUid, safeList(request.getRecurring()), response);

        // Bill-split sync is intentionally backend-owned and not processed here yet.
        response.getSyncedItems().setBillSplit(0);
        return response;
    }

    @Transactional(readOnly = true)
    public SyncPullResponseDto pull(
            String firebaseUid,
            LocalDateTime expenseSince,
            LocalDateTime categorySince,
            LocalDateTime budgetSince,
            LocalDateTime goalSince,
            LocalDateTime recurringSince) {
        SyncPullResponseDto response = SyncPullResponseDto.empty();

        List<ExpenseEntity> expenses = expenseRepository.findChangedSince(firebaseUid, expenseSince);
        response.setExpenses(expenses.stream().map(this::toExpenseItem).toList());

        List<CategoryEntity> categories = categoryRepository.findChangedSince(firebaseUid, categorySince);
        response.setCategories(categories.stream().map(this::toCategoryItem).toList());

        List<BudgetEntity> budgets = budgetRepository.findChangedSince(firebaseUid, budgetSince);
        response.setBudgets(budgets.stream().map(budget -> toBudgetItem(firebaseUid, budget)).toList());

        List<SavingsGoalEntity> goals = savingsGoalRepository.findChangedSince(firebaseUid, goalSince);
        response.setGoals(goals.stream().map(this::toGoalItem).toList());

        List<RecurringExpenseEntity> recurring = recurringExpenseRepository.findChangedSince(
                firebaseUid,
                recurringSince);
        response.setRecurring(recurring.stream().map(this::toRecurringItem).toList());

        return response;
    }

    private void syncCategories(
            String firebaseUid,
            List<SyncPushRequestDto.CategoryItem> items,
            SyncPushResponseDto response) {
        for (SyncPushRequestDto.CategoryItem item : items) {
            UUID id = parseUuid(item.getId());
            if (id == null) {
                addFailed(response, item.getId(), "category", "Invalid category id");
                continue;
            }

            Optional<CategoryEntity> existingOpt = categoryRepository.findById(id);
            if (existingOpt.isPresent() && !firebaseUid.equals(existingOpt.get().getFirebaseUid())) {
                addFailed(response, item.getId(), "category", "Forbidden category ownership");
                continue;
            }

            boolean seeded = false;
            if (existingOpt.isEmpty()) {
                seedCategoryRow(id, firebaseUid);
                existingOpt = categoryRepository.findById(id);
                seeded = true;
            }
            CategoryEntity entity = existingOpt.orElseGet(CategoryEntity::new);
            LocalDateTime incomingUpdatedAt = parseDateTime(item.getUpdatedAt());
            if (!seeded && isStale(entity.getUpdatedAt(), incomingUpdatedAt)) {
                addFailed(response, item.getId(), "category", "Stale category update");
                continue;
            }

            String normalizedName = normalizeText(item.getName(), "Uncategorized");
            String normalizedCategoryType = normalizeCategoryType(item.getCategoryType());
            boolean incomingDeleted = Boolean.TRUE.equals(item.getIsDeleted());

            if (!incomingDeleted) {
                Optional<CategoryEntity> duplicateActiveCategory = findActiveCategoryDuplicate(
                        firebaseUid,
                        id,
                        normalizedName,
                        normalizedCategoryType);
                if (duplicateActiveCategory.isPresent()) {
                    UUID canonicalId = duplicateActiveCategory.get().getId();
                    remapCategoryReferences(firebaseUid, id, canonicalId);
                    LocalDateTime incomingDeletedAt = parseDateTime(item.getDeletedAt());

                    entity.setId(id);
                    entity.setFirebaseUid(firebaseUid);
                    entity.setName(normalizedName);
                    entity.setIcon(item.getIcon());
                    entity.setColor(item.getColor());
                    entity.setIsDefault(Boolean.TRUE.equals(item.getIsDefault()));
                    entity.setCategoryType(normalizedCategoryType);
                    entity.setSortOrder(item.getSortOrder());
                    entity.setIsDeleted(true);
                    entity.setDeletedAt(incomingDeletedAt == null
                            ? resolveUpdatedAt(item.getUpdatedAt())
                            : incomingDeletedAt);
                    entity.setRetryCount(item.getRetryCount());
                    entity.setLastError(item.getLastError());
                    entity.setCreatedAt(resolveCreatedAt(entity.getCreatedAt(), item.getCreatedAt()));
                    entity.setUpdatedAt(resolveUpdatedAt(item.getUpdatedAt()));
                    entity.setSyncedAt(resolveUpdatedAt(item.getSyncedAt()));

                    categoryRepository.save(entity);
                    response.getSyncedItems().setCategories(response.getSyncedItems().getCategories() + 1);
                    log.warn(
                            "Merged duplicate category {} into canonical {} for user {}",
                            id,
                            canonicalId,
                            firebaseUid);
                    continue;
                }
            }

            entity.setId(id);
            entity.setFirebaseUid(firebaseUid);
            entity.setName(normalizedName);
            entity.setIcon(item.getIcon());
            entity.setColor(item.getColor());
            entity.setIsDefault(Boolean.TRUE.equals(item.getIsDefault()));
            entity.setCategoryType(normalizedCategoryType);
            entity.setSortOrder(item.getSortOrder());
            entity.setIsDeleted(incomingDeleted);
            entity.setDeletedAt(parseDateTime(item.getDeletedAt()));
            entity.setRetryCount(item.getRetryCount());
            entity.setLastError(item.getLastError());
            entity.setCreatedAt(resolveCreatedAt(entity.getCreatedAt(), item.getCreatedAt()));
            entity.setUpdatedAt(resolveUpdatedAt(item.getUpdatedAt()));
            entity.setSyncedAt(resolveUpdatedAt(item.getSyncedAt()));

            categoryRepository.save(entity);
            response.getSyncedItems().setCategories(response.getSyncedItems().getCategories() + 1);
        }
    }

    private Optional<CategoryEntity> findActiveCategoryDuplicate(
            String firebaseUid,
            UUID categoryId,
            String name,
            String categoryType) {
        return categoryRepository.findActiveDuplicatesByNameAndTypeExcludingId(
                        firebaseUid,
                        name,
                        categoryType,
                        categoryId)
                .stream()
                .findFirst();
    }

    private void remapCategoryReferences(String firebaseUid, UUID fromCategoryId, UUID toCategoryId) {
        if (fromCategoryId == null || toCategoryId == null || fromCategoryId.equals(toCategoryId)) {
            return;
        }

        entityManager.createNativeQuery("""
                update expenses
                set category_id = :toCategoryId,
                    updated_at = now()
                where firebase_uid = :firebaseUid
                  and category_id = :fromCategoryId
                """)
                .setParameter("toCategoryId", toCategoryId)
                .setParameter("firebaseUid", firebaseUid)
                .setParameter("fromCategoryId", fromCategoryId)
                .executeUpdate();

        entityManager.createNativeQuery("""
                update recurring_expenses
                set category_id = :toCategoryId,
                    updated_at = now()
                where firebase_uid = :firebaseUid
                  and category_id = :fromCategoryId
                """)
                .setParameter("toCategoryId", toCategoryId)
                .setParameter("firebaseUid", firebaseUid)
                .setParameter("fromCategoryId", fromCategoryId)
                .executeUpdate();

        entityManager.createNativeQuery("""
                update category_budgets cb
                set category_id = :toCategoryId
                from budgets b
                where cb.budget_id = b.id
                  and b.firebase_uid = :firebaseUid
                  and cb.category_id = :fromCategoryId
                """)
                .setParameter("toCategoryId", toCategoryId)
                .setParameter("firebaseUid", firebaseUid)
                .setParameter("fromCategoryId", fromCategoryId)
                .executeUpdate();
    }

    private void syncExpenses(
            String firebaseUid,
            List<SyncPushRequestDto.ExpenseItem> items,
            SyncPushResponseDto response) {
        for (SyncPushRequestDto.ExpenseItem item : items) {
            UUID id = parseUuid(item.getId());
            if (id == null) {
                addFailed(response, item.getId(), "expense", "Invalid expense id");
                continue;
            }

            Optional<ExpenseEntity> existingOpt = expenseRepository.findById(id);
            if (existingOpt.isPresent() && !firebaseUid.equals(existingOpt.get().getFirebaseUid())) {
                addFailed(response, item.getId(), "expense", "Forbidden expense ownership");
                continue;
            }

            UUID categoryId = parseUuid(item.getCategoryId());
            if (categoryId != null) {
                Optional<CategoryEntity> categoryOpt = categoryRepository.findById(categoryId);
                if (categoryOpt.isEmpty() || !firebaseUid.equals(categoryOpt.get().getFirebaseUid())) {
                    addFailed(response, item.getId(), "expense", "Invalid category reference");
                    continue;
                }
            }

            boolean seeded = false;
            if (existingOpt.isEmpty()) {
                seedExpenseRow(id, firebaseUid);
                existingOpt = expenseRepository.findById(id);
                seeded = true;
            }
            ExpenseEntity entity = existingOpt.orElseGet(ExpenseEntity::new);
            LocalDateTime incomingUpdatedAt = parseDateTime(item.getUpdatedAt());
            if (!seeded && isStale(entity.getUpdatedAt(), incomingUpdatedAt)) {
                addFailed(response, item.getId(), "expense", "Stale expense update");
                continue;
            }

            entity.setId(id);
            entity.setFirebaseUid(firebaseUid);
            entity.setAmount(item.getAmount() == null ? 0.0 : item.getAmount());
            entity.setTransactionType(normalizeExpenseType(item.getTransactionType()));
            entity.setCategoryId(categoryId);
            entity.setDate(resolveExpenseDate(item.getDate()));
            entity.setNote(item.getNotes());
            entity.setMerchant(item.getMerchant());
            entity.setNoteSummary(item.getNoteSummary());
            entity.setAiCategoryId(parseUuid(item.getAiCategoryId()));
            entity.setAiConfidence(item.getAiConfidence());
            entity.setAiSource(item.getAiSource());
            entity.setAiLastUpdated(parseDateTime(item.getAiLastUpdated()));
            entity.setRecurringExpenseId(parseUuid(item.getRecurringExpenseId()));
            entity.setReceiptPaths(item.getReceiptPaths());
            entity.setCurrency(normalizeText(item.getCurrency(), "USD"));
            entity.setOriginalAmount(toBigDecimalNullable(item.getOriginalAmount()));
            entity.setExchangeRate(toBigDecimalNullable(item.getExchangeRate()));
            entity.setRateSource(item.getRateSource());
            entity.setIsDeleted(Boolean.TRUE.equals(item.getIsDeleted()));
            entity.setDeletedAt(parseDateTime(item.getDeletedAt()));
            entity.setRetryCount(item.getRetryCount());
            entity.setLastError(item.getLastError());
            entity.setCreatedAt(resolveCreatedAt(entity.getCreatedAt(), item.getCreatedAt()));
            entity.setUpdatedAt(resolveUpdatedAt(item.getUpdatedAt()));
            entity.setSyncedAt(resolveUpdatedAt(item.getSyncedAt()));

            expenseRepository.save(entity);
            response.getSyncedItems().setExpenses(response.getSyncedItems().getExpenses() + 1);
        }
    }

    private void syncBudgets(
            String firebaseUid,
            List<SyncPushRequestDto.BudgetItem> items,
            SyncPushResponseDto response) {
        for (SyncPushRequestDto.BudgetItem item : items) {
            UUID id = parseUuid(item.getId());
            if (id == null) {
                addFailed(response, item.getId(), "budget", "Invalid budget id");
                continue;
            }

            Optional<BudgetEntity> existingOpt = budgetRepository.findById(id);
            if (existingOpt.isPresent() && !firebaseUid.equals(existingOpt.get().getFirebaseUid())) {
                addFailed(response, item.getId(), "budget", "Forbidden budget ownership");
                continue;
            }

            boolean seeded = false;
            if (existingOpt.isEmpty()) {
                seedBudgetRow(id, firebaseUid);
                existingOpt = budgetRepository.findById(id);
                seeded = true;
            }
            BudgetEntity entity = existingOpt.orElseGet(BudgetEntity::new);
            LocalDateTime incomingUpdatedAt = parseDateTime(item.getUpdatedAt());
            if (!seeded && isStale(entity.getUpdatedAt(), incomingUpdatedAt)) {
                addFailed(response, item.getId(), "budget", "Stale budget update");
                continue;
            }

            entity.setId(id);
            entity.setFirebaseUid(firebaseUid);
            entity.setMonth(normalizeText(item.getMonth(), ""));
            entity.setTotalAmount(toBigDecimal(item.getTotalAmount()));
            entity.setIsDeleted(Boolean.TRUE.equals(item.getIsDeleted()));
            entity.setDeletedAt(parseDateTime(item.getDeletedAt()));
            entity.setRetryCount(item.getRetryCount());
            entity.setLastError(item.getLastError());
            entity.setCreatedAt(resolveCreatedAt(entity.getCreatedAt(), item.getCreatedAt()));
            entity.setUpdatedAt(resolveUpdatedAt(item.getUpdatedAt()));
            entity.setSyncedAt(resolveUpdatedAt(item.getSyncedAt()));

            budgetRepository.save(entity);
            categoryBudgetRepository.deleteByBudgetId(entity.getId());
            upsertCategoryBudgets(firebaseUid, entity.getId(), item.getCategoryBudgets());

            response.getSyncedItems().setBudgets(response.getSyncedItems().getBudgets() + 1);
        }
    }

    private void syncGoals(
            String firebaseUid,
            List<SyncPushRequestDto.GoalItem> items,
            SyncPushResponseDto response) {
        for (SyncPushRequestDto.GoalItem item : items) {
            UUID id = parseUuid(item.getId());
            if (id == null) {
                addFailed(response, item.getId(), "goal", "Invalid goal id");
                continue;
            }

            Optional<SavingsGoalEntity> existingOpt = savingsGoalRepository.findById(id);
            if (existingOpt.isPresent() && !firebaseUid.equals(existingOpt.get().getFirebaseUid())) {
                addFailed(response, item.getId(), "goal", "Forbidden goal ownership");
                continue;
            }

            boolean seeded = false;
            if (existingOpt.isEmpty()) {
                seedGoalRow(id, firebaseUid);
                existingOpt = savingsGoalRepository.findById(id);
                seeded = true;
            }
            SavingsGoalEntity entity = existingOpt.orElseGet(SavingsGoalEntity::new);
            LocalDateTime incomingUpdatedAt = parseDateTime(item.getUpdatedAt());
            if (!seeded && isStale(entity.getUpdatedAt(), incomingUpdatedAt)) {
                addFailed(response, item.getId(), "goal", "Stale goal update");
                continue;
            }

            entity.setId(id);
            entity.setFirebaseUid(firebaseUid);
            entity.setName(normalizeText(item.getName(), "Goal"));
            entity.setTargetAmount(toBigDecimal(item.getTargetAmount()));
            entity.setCurrentAmount(toBigDecimal(item.getCurrentAmount()));
            entity.setDeadline(parseDateTime(item.getDeadline()));
            entity.setColor(item.getColor());
            entity.setIcon(item.getIcon());
            entity.setIsArchived(Boolean.TRUE.equals(item.getIsArchived()));
            entity.setIsDeleted(Boolean.TRUE.equals(item.getIsDeleted()));
            entity.setDeletedAt(parseDateTime(item.getDeletedAt()));
            entity.setRetryCount(item.getRetryCount());
            entity.setLastError(item.getLastError());
            entity.setCreatedAt(resolveCreatedAt(entity.getCreatedAt(), item.getCreatedAt()));
            entity.setUpdatedAt(resolveUpdatedAt(item.getUpdatedAt()));
            entity.setSyncedAt(resolveUpdatedAt(item.getSyncedAt()));

            savingsGoalRepository.save(entity);
            goalTransactionRepository.deleteByGoalId(entity.getId());
            upsertGoalTransactions(entity.getId(), item.getTransactions());

            response.getSyncedItems().setGoals(response.getSyncedItems().getGoals() + 1);
        }
    }

    private void syncRecurring(
            String firebaseUid,
            List<SyncPushRequestDto.RecurringItem> items,
            SyncPushResponseDto response) {
        for (SyncPushRequestDto.RecurringItem item : items) {
            UUID id = parseUuid(item.getId());
            if (id == null) {
                addFailed(response, item.getId(), "recurring", "Invalid recurring id");
                continue;
            }

            Optional<RecurringExpenseEntity> existingOpt = recurringExpenseRepository.findById(id);
            if (existingOpt.isPresent() && !firebaseUid.equals(existingOpt.get().getFirebaseUid())) {
                addFailed(response, item.getId(), "recurring", "Forbidden recurring ownership");
                continue;
            }

            UUID categoryId = parseUuid(item.getCategoryId());
            if (categoryId != null) {
                Optional<CategoryEntity> categoryOpt = categoryRepository.findById(categoryId);
                if (categoryOpt.isEmpty() || !firebaseUid.equals(categoryOpt.get().getFirebaseUid())) {
                    addFailed(response, item.getId(), "recurring", "Invalid category reference");
                    continue;
                }
            }

            boolean seeded = false;
            if (existingOpt.isEmpty()) {
                seedRecurringRow(id, firebaseUid);
                existingOpt = recurringExpenseRepository.findById(id);
                seeded = true;
            }
            RecurringExpenseEntity entity = existingOpt.orElseGet(RecurringExpenseEntity::new);
            LocalDateTime incomingUpdatedAt = parseDateTime(item.getUpdatedAt());
            if (!seeded && isStale(entity.getUpdatedAt(), incomingUpdatedAt)) {
                addFailed(response, item.getId(), "recurring", "Stale recurring update");
                continue;
            }

            entity.setId(id);
            entity.setFirebaseUid(firebaseUid);
            entity.setAmount(toBigDecimal(item.getAmount()));
            entity.setCategoryId(categoryId);
            entity.setNotes(item.getNotes());
            entity.setFrequency(normalizeText(item.getFrequency(), "monthly"));
            entity.setCurrency(normalizeText(item.getCurrency(), "USD"));
            entity.setOriginalAmount(toBigDecimalNullable(item.getOriginalAmount()));
            entity.setExchangeRate(toBigDecimalNullable(item.getExchangeRate()));
            entity.setStartDate(resolveDateTimeOrNow(item.getStartDate()));
            entity.setEndDate(parseDateTime(item.getEndDate()));
            entity.setLastGenerated(parseDateTime(item.getLastGenerated()));
            entity.setNextDueDate(resolveDateTimeOrNow(item.getNextDueDate()));
            entity.setIsActive(!Boolean.FALSE.equals(item.getIsActive()));
            entity.setNotificationEnabled(Boolean.TRUE.equals(item.getNotificationEnabled()));
            entity.setNotificationDaysBefore(item.getNotificationDaysBefore() == null
                    ? 0
                    : item.getNotificationDaysBefore());
            entity.setIsDeleted(Boolean.TRUE.equals(item.getIsDeleted()));
            entity.setDeletedAt(parseDateTime(item.getDeletedAt()));
            entity.setRetryCount(item.getRetryCount());
            entity.setLastError(item.getLastError());
            entity.setCreatedAt(resolveCreatedAt(entity.getCreatedAt(), item.getCreatedAt()));
            entity.setUpdatedAt(resolveUpdatedAt(item.getUpdatedAt()));
            entity.setSyncedAt(resolveUpdatedAt(item.getSyncedAt()));

            recurringExpenseRepository.save(entity);
            response.getSyncedItems().setRecurring(response.getSyncedItems().getRecurring() + 1);
        }
    }

    private void seedCategoryRow(UUID id, String firebaseUid) {
        entityManager.createNativeQuery("""
                insert into categories (id, firebase_uid, name, icon, color, category_type)
                values (:id, :firebaseUid, :name, :icon, :color, :categoryType)
                on conflict (id) do nothing
                """)
                .setParameter("id", id)
                .setParameter("firebaseUid", firebaseUid)
                .setParameter("name", "Uncategorized")
                .setParameter("icon", "circle")
                .setParameter("color", "#425d84ff")
                .setParameter("categoryType", "EXPENSE")
                .executeUpdate();
    }

    private void seedExpenseRow(UUID id, String firebaseUid) {
        entityManager.createNativeQuery("""
                insert into expenses (id, firebase_uid, amount, date, transaction_type)
                values (:id, :firebaseUid, :amount, :dateValue, :transactionType)
                on conflict (id) do nothing
                """)
                .setParameter("id", id)
                .setParameter("firebaseUid", firebaseUid)
                .setParameter("amount", BigDecimal.ZERO)
                .setParameter("dateValue", utcNow())
                .setParameter("transactionType", "EXPENSE")
                .executeUpdate();
    }

    private void seedBudgetRow(UUID id, String firebaseUid) {
        entityManager.createNativeQuery("""
                insert into budgets (id, firebase_uid, month, total_amount)
                values (:id, :firebaseUid, :month, :totalAmount)
                on conflict (id) do nothing
                """)
                .setParameter("id", id)
                .setParameter("firebaseUid", firebaseUid)
                .setParameter("month", "1970-01")
                .setParameter("totalAmount", BigDecimal.ZERO)
                .executeUpdate();
    }

    private void seedGoalRow(UUID id, String firebaseUid) {
        entityManager.createNativeQuery("""
                insert into savings_goals (id, firebase_uid, name, target_amount, color, icon)
                values (:id, :firebaseUid, :name, :targetAmount, :color, :icon)
                on conflict (id) do nothing
                """)
                .setParameter("id", id)
                .setParameter("firebaseUid", firebaseUid)
                .setParameter("name", "Goal")
                .setParameter("targetAmount", BigDecimal.ZERO)
                .setParameter("color", "#10B981")
                .setParameter("icon", "target")
                .executeUpdate();
    }

    private void seedRecurringRow(UUID id, String firebaseUid) {
        entityManager.createNativeQuery("""
                insert into recurring_expenses (id, firebase_uid, amount, frequency, start_date, next_due_date)
                values (:id, :firebaseUid, :amount, :frequency, :startDate, :nextDueDate)
                on conflict (id) do nothing
                """)
                .setParameter("id", id)
                .setParameter("firebaseUid", firebaseUid)
                .setParameter("amount", BigDecimal.ZERO)
                .setParameter("frequency", "monthly")
                .setParameter("startDate", utcNow())
                .setParameter("nextDueDate", utcNow())
                .executeUpdate();
    }

    private void upsertCategoryBudgets(
            String firebaseUid,
            UUID budgetId,
            List<SyncPushRequestDto.CategoryBudgetItem> items) {
        if (items == null) {
            return;
        }

        for (SyncPushRequestDto.CategoryBudgetItem item : items) {
            UUID categoryId = parseUuid(item.getCategoryId());
            if (categoryId != null) {
                Optional<CategoryEntity> categoryOpt = categoryRepository.findById(categoryId);
                if (categoryOpt.isEmpty() || !firebaseUid.equals(categoryOpt.get().getFirebaseUid())) {
                    continue;
                }
            }

            CategoryBudgetEntity entity = CategoryBudgetEntity.builder()
                    .budgetId(budgetId)
                    .categoryId(categoryId)
                    .amount(toBigDecimal(item.getAmount()))
                    .syncedAt(utcNow())
                    .syncStatus("synced")
                    .build();
            categoryBudgetRepository.save(entity);
        }
    }

    private void upsertGoalTransactions(UUID goalId, List<SyncPushRequestDto.GoalTransactionItem> items) {
        if (items == null) {
            return;
        }

        for (SyncPushRequestDto.GoalTransactionItem item : items) {
            UUID id = parseUuid(item.getId());
            if (id == null) {
                continue;
            }

            GoalTransactionEntity entity = GoalTransactionEntity.builder()
                    .id(id)
                    .goalId(goalId)
                    .amount(toBigDecimal(item.getAmount()))
                    .type(normalizeText(item.getType(), "deposit"))
                    .note(item.getNote())
                    .date(resolveDateTimeOrNow(item.getDate()))
                    .build();
            goalTransactionRepository.save(entity);
        }
    }

    private SyncPullResponseDto.ExpenseItem toExpenseItem(ExpenseEntity entity) {
        return SyncPullResponseDto.ExpenseItem.builder()
                .id(entity.getId().toString())
                .amount(entity.getAmount())
                .transactionType(entity.getTransactionType())
                .categoryId(toString(entity.getCategoryId()))
                .date(formatOffsetDateTime(entity.getDate()))
                .notes(entity.getNote())
                .merchant(entity.getMerchant())
                .noteSummary(entity.getNoteSummary())
                .aiCategoryId(toString(entity.getAiCategoryId()))
                .aiConfidence(entity.getAiConfidence())
                .aiSource(entity.getAiSource())
                .aiLastUpdated(formatDateTime(entity.getAiLastUpdated()))
                .recurringExpenseId(toString(entity.getRecurringExpenseId()))
                .receiptPaths(entity.getReceiptPaths() == null ? List.of() : entity.getReceiptPaths())
                .currency(entity.getCurrency())
                .originalAmount(entity.getOriginalAmount() == null ? null : entity.getOriginalAmount().doubleValue())
                .exchangeRate(entity.getExchangeRate() == null ? null : entity.getExchangeRate().doubleValue())
                .rateSource(entity.getRateSource())
                .createdAt(formatDateTime(entity.getCreatedAt()))
                .updatedAt(formatDateTime(entity.getUpdatedAt()))
                .syncedAt(formatDateTime(entity.getSyncedAt()))
                .isDeleted(Boolean.TRUE.equals(entity.getIsDeleted()))
                .deletedAt(formatDateTime(entity.getDeletedAt()))
                .retryCount(entity.getRetryCount())
                .lastError(entity.getLastError())
                .build();
    }

    private SyncPullResponseDto.CategoryItem toCategoryItem(CategoryEntity entity) {
        return SyncPullResponseDto.CategoryItem.builder()
                .id(entity.getId().toString())
                .name(entity.getName())
                .icon(entity.getIcon())
                .color(entity.getColor())
                .isDefault(entity.getIsDefault())
                .categoryType(entity.getCategoryType())
                .sortOrder(entity.getSortOrder())
                .createdAt(formatDateTime(entity.getCreatedAt()))
                .updatedAt(formatDateTime(entity.getUpdatedAt()))
                .syncedAt(formatDateTime(entity.getSyncedAt()))
                .isDeleted(Boolean.TRUE.equals(entity.getIsDeleted()))
                .deletedAt(formatDateTime(entity.getDeletedAt()))
                .retryCount(entity.getRetryCount())
                .lastError(entity.getLastError())
                .build();
    }

    private SyncPullResponseDto.BudgetItem toBudgetItem(String firebaseUid, BudgetEntity entity) {
        List<CategoryBudgetEntity> budgets = categoryBudgetRepository.findByBudgetIdAndFirebaseUid(
                entity.getId(),
                firebaseUid);
        List<SyncPullResponseDto.CategoryBudgetItem> categoryBudgets = budgets.stream()
                .map(item -> SyncPullResponseDto.CategoryBudgetItem.builder()
                        .categoryId(toString(item.getCategoryId()))
                        .amount(item.getAmount() == null ? 0.0 : item.getAmount().doubleValue())
                        .build())
                .toList();

        return SyncPullResponseDto.BudgetItem.builder()
                .id(entity.getId().toString())
                .month(entity.getMonth())
                .totalAmount(entity.getTotalAmount() == null ? 0.0 : entity.getTotalAmount().doubleValue())
                .categoryBudgets(categoryBudgets)
                .createdAt(formatDateTime(entity.getCreatedAt()))
                .updatedAt(formatDateTime(entity.getUpdatedAt()))
                .syncedAt(formatDateTime(entity.getSyncedAt()))
                .isDeleted(Boolean.TRUE.equals(entity.getIsDeleted()))
                .deletedAt(formatDateTime(entity.getDeletedAt()))
                .retryCount(entity.getRetryCount())
                .lastError(entity.getLastError())
                .build();
    }

    private SyncPullResponseDto.GoalItem toGoalItem(SavingsGoalEntity entity) {
        List<GoalTransactionEntity> transactions = goalTransactionRepository.findByGoalIdOrderByDateDesc(
                entity.getId());
        List<SyncPullResponseDto.GoalTransactionItem> goalTransactions = transactions.stream()
                .map(item -> SyncPullResponseDto.GoalTransactionItem.builder()
                        .id(item.getId().toString())
                        .amount(item.getAmount() == null ? 0.0 : item.getAmount().doubleValue())
                        .type(item.getType())
                        .note(item.getNote())
                        .date(formatDateTime(item.getDate()))
                        .build())
                .toList();

        return SyncPullResponseDto.GoalItem.builder()
                .id(entity.getId().toString())
                .name(entity.getName())
                .targetAmount(entity.getTargetAmount() == null ? 0.0 : entity.getTargetAmount().doubleValue())
                .currentAmount(entity.getCurrentAmount() == null ? 0.0 : entity.getCurrentAmount().doubleValue())
                .deadline(formatDateTime(entity.getDeadline()))
                .color(entity.getColor())
                .icon(entity.getIcon())
                .isArchived(entity.getIsArchived())
                .transactions(goalTransactions)
                .createdAt(formatDateTime(entity.getCreatedAt()))
                .updatedAt(formatDateTime(entity.getUpdatedAt()))
                .syncedAt(formatDateTime(entity.getSyncedAt()))
                .isDeleted(Boolean.TRUE.equals(entity.getIsDeleted()))
                .deletedAt(formatDateTime(entity.getDeletedAt()))
                .retryCount(entity.getRetryCount())
                .lastError(entity.getLastError())
                .build();
    }

    private SyncPullResponseDto.RecurringItem toRecurringItem(RecurringExpenseEntity entity) {
        return SyncPullResponseDto.RecurringItem.builder()
                .id(entity.getId().toString())
                .amount(entity.getAmount() == null ? 0.0 : entity.getAmount().doubleValue())
                .categoryId(toString(entity.getCategoryId()))
                .notes(entity.getNotes())
                .frequency(entity.getFrequency())
                .currency(entity.getCurrency())
                .originalAmount(entity.getOriginalAmount() == null ? null : entity.getOriginalAmount().doubleValue())
                .exchangeRate(entity.getExchangeRate() == null ? null : entity.getExchangeRate().doubleValue())
                .startDate(formatDateTime(entity.getStartDate()))
                .endDate(formatDateTime(entity.getEndDate()))
                .lastGenerated(formatDateTime(entity.getLastGenerated()))
                .nextDueDate(formatDateTime(entity.getNextDueDate()))
                .isActive(entity.getIsActive())
                .notificationEnabled(entity.getNotificationEnabled())
                .notificationDaysBefore(entity.getNotificationDaysBefore())
                .createdAt(formatDateTime(entity.getCreatedAt()))
                .updatedAt(formatDateTime(entity.getUpdatedAt()))
                .syncedAt(formatDateTime(entity.getSyncedAt()))
                .isDeleted(Boolean.TRUE.equals(entity.getIsDeleted()))
                .deletedAt(formatDateTime(entity.getDeletedAt()))
                .retryCount(entity.getRetryCount())
                .lastError(entity.getLastError())
                .build();
    }

    private boolean isStale(LocalDateTime existingUpdatedAt, LocalDateTime incomingUpdatedAt) {
        return existingUpdatedAt != null && incomingUpdatedAt != null && incomingUpdatedAt.isBefore(existingUpdatedAt);
    }

    private void addFailed(SyncPushResponseDto response, String id, String entityType, String error) {
        log.warn("Sync failed for {} id {}: {}", entityType, id, error);
        response.getFailedItems().add(SyncPushResponseDto.FailedItem.builder()
                .id(id)
                .entityType(entityType)
                .error(error)
                .build());
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalizeExpenseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "EXPENSE";
        }
        return "INCOME".equalsIgnoreCase(raw) ? "INCOME" : "EXPENSE";
    }

    private String normalizeCategoryType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "EXPENSE";
        }
        return raw.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private OffsetDateTime resolveExpenseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        try {
            return LocalDate.parse(raw).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        LocalDateTime dateTime = parseDateTime(raw);
        return dateTime == null ? OffsetDateTime.now(ZoneOffset.UTC) : dateTime.atOffset(ZoneOffset.UTC);
    }

    private LocalDateTime resolveDateTimeOrNow(String raw) {
        LocalDateTime parsed = parseDateTime(raw);
        return parsed == null ? utcNow() : parsed;
    }

    private LocalDateTime resolveCreatedAt(LocalDateTime existing, String incoming) {
        if (existing != null) {
            return existing;
        }
        LocalDateTime parsed = parseDateTime(incoming);
        return parsed == null ? utcNow() : parsed;
    }

    private LocalDateTime resolveUpdatedAt(String incoming) {
        LocalDateTime parsed = parseDateTime(incoming);
        return parsed == null ? utcNow() : parsed;
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(raw)
                    .atOffset(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (Exception ignored) {
        }
        return null;
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC).toString();
    }

    private String formatOffsetDateTime(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC).toString();
    }

    private String toString(UUID value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }

    private BigDecimal toBigDecimalNullable(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }
}
