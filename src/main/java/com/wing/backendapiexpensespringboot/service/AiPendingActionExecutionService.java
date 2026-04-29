package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.agent.AgentProposal;
import com.wing.backendapiexpensespringboot.dto.agent.BudgetProposal;
import com.wing.backendapiexpensespringboot.dto.agent.CategoryProposal;
import com.wing.backendapiexpensespringboot.dto.agent.GoalProposal;
import com.wing.backendapiexpensespringboot.dto.agent.RecurringExpenseProposal;
import com.wing.backendapiexpensespringboot.dto.agent.TransactionProposal;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.CategoryType;
import com.wing.backendapiexpensespringboot.model.RecurringExpenseEntity;
import com.wing.backendapiexpensespringboot.model.SavingsGoalEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.RecurringExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPendingActionExecutionService {

    private final PendingAiActionService pendingAiActionService;
    private final ExpenseService expenseService;
    private final CategoryService categoryService;
    private final BudgetRepository budgetRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final RecurringExpenseRepository recurringExpenseRepository;

    @Transactional
    public void confirmAndExecute(String firebaseUid, UUID actionId) {
        AgentProposal proposal = pendingAiActionService.confirm(firebaseUid, actionId);

        if (proposal instanceof TransactionProposal transactionProposal) {
            applyTransactionProposal(firebaseUid, transactionProposal);
            return;
        }
        if (proposal instanceof BudgetProposal budgetProposal) {
            applyBudgetProposal(firebaseUid, budgetProposal);
            return;
        }
        if (proposal instanceof GoalProposal goalProposal) {
            applyGoalProposal(firebaseUid, goalProposal);
            return;
        }
        if (proposal instanceof CategoryProposal categoryProposal) {
            applyCategoryProposal(firebaseUid, categoryProposal);
            return;
        }
        if (proposal instanceof RecurringExpenseProposal recurringExpenseProposal) {
            applyRecurringProposal(firebaseUid, recurringExpenseProposal);
            return;
        }

        throw AppException.badRequest("Unsupported pending action payload");
    }

    @Transactional
    public void cancel(String firebaseUid, UUID actionId) {
        pendingAiActionService.cancel(firebaseUid, actionId);
    }

    private void applyTransactionProposal(String firebaseUid, TransactionProposal proposal) {
        List<TransactionProposal.TransactionItem> items = proposal.transactions();
        if (items == null || items.isEmpty()) {
            throw AppException.badRequest("Pending transaction action has no items");
        }

        for (TransactionProposal.TransactionItem item : items) {
            if (item == null || item.amount() == null || item.amount() <= 0) {
                throw AppException.badRequest("Transaction amount is required and must be greater than zero");
            }

            String normalizedType = normalizeTransactionType(item.type());
            String rawCategory = normalizeText(item.category());
            if (rawCategory == null) {
                throw AppException.badRequest("Transaction category is required");
            }

            CategoryType categoryType = "INCOME".equals(normalizedType)
                    ? CategoryType.INCOME
                    : CategoryType.EXPENSE;
            CategoryEntity category = resolveOrCreateCategory(firebaseUid, rawCategory, categoryType);

            Map<String, Object> expenseData = new HashMap<>();
            expenseData.put("amount", item.amount());
            expenseData.put("transactionType", normalizedType);
            expenseData.put("currency", defaultIfBlank(item.currency(), "USD"));
            expenseData.put("merchant", nullable(item.merchant()));
            expenseData.put("date", defaultIfBlank(item.date(), LocalDate.now(ZoneOffset.UTC).toString()));
            expenseData.put("note", firstNonBlank(item.note(), item.merchant(), "Transaction"));
            expenseData.put("noteSummary", firstNonBlank(item.noteSummary(), item.note(), item.merchant(), "AI transaction"));
            expenseData.put("categoryId", category.getId());

            expenseService.createExpense(firebaseUid, expenseData);
        }
    }

    private void applyBudgetProposal(String firebaseUid, BudgetProposal proposal) {
        if (proposal.totalAmount() == null || proposal.totalAmount() <= 0) {
            throw AppException.badRequest("Budget total amount is required and must be greater than zero");
        }

        String month = normalizeBudgetMonth(proposal.month());
        BudgetEntity budget = budgetRepository.findByMonthAndFirebaseUid(month, firebaseUid)
                .orElseGet(BudgetEntity::new);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (budget.getId() == null) {
            budget.setFirebaseUid(firebaseUid);
            budget.setMonth(month);
            budget.setCreatedAt(now);
        }

        budget.setTotalAmount(BigDecimal.valueOf(proposal.totalAmount()));
        budget.setIsDeleted(false);
        budget.setDeletedAt(null);
        budget.setUpdatedAt(now);
        budget.setSyncStatus("pending");
        budget.setSyncedAt(null);

        budgetRepository.save(budget);
    }

    private void applyGoalProposal(String firebaseUid, GoalProposal proposal) {
        if (proposal.targetAmount() == null || proposal.targetAmount() <= 0) {
            throw AppException.badRequest("Goal target amount is required and must be greater than zero");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        SavingsGoalEntity goal = SavingsGoalEntity.builder()
                .firebaseUid(firebaseUid)
                .name(firstNonBlank(proposal.name(), "Goal"))
                .targetAmount(BigDecimal.valueOf(proposal.targetAmount()))
                .currentAmount(BigDecimal.valueOf(defaultIfNull(proposal.currentAmount(), 0.0)))
                .deadline(parseOptionalDate(proposal.deadline()))
                .color(defaultIfBlank(proposal.color(), "#10B981"))
                .icon(defaultIfBlank(proposal.icon(), "target"))
                .isArchived(false)
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .syncStatus("pending")
                .build();

        savingsGoalRepository.save(goal);
    }

    private void applyCategoryProposal(String firebaseUid, CategoryProposal proposal) {
        String name = normalizeText(proposal.name());
        if (name == null) {
            throw AppException.badRequest("Category name is required");
        }

        CategoryType categoryType = parseCategoryType(proposal.categoryType());
        resolveOrCreateCategory(
                firebaseUid,
                name,
                categoryType,
                defaultIfBlank(proposal.icon(), "tag"),
                defaultIfBlank(proposal.color(), "#6366F1"));
    }

    private void applyRecurringProposal(String firebaseUid, RecurringExpenseProposal proposal) {
        if (proposal.amount() == null || proposal.amount() <= 0) {
            throw AppException.badRequest("Recurring amount is required and must be greater than zero");
        }

        String categoryName = normalizeText(proposal.category());
        if (categoryName == null) {
            throw AppException.badRequest("Recurring category is required");
        }

        CategoryType categoryType = parseCategoryType(proposal.type());
        CategoryEntity category = resolveOrCreateCategory(firebaseUid, categoryName, categoryType);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startDate = parseRequiredDate(proposal.startDate(), "Recurring start date is required");
        RecurringExpenseEntity recurring = RecurringExpenseEntity.builder()
                .firebaseUid(firebaseUid)
                .amount(BigDecimal.valueOf(proposal.amount()))
                .categoryId(category.getId())
                .notes(nullable(proposal.note()))
                .frequency(normalizeFrequency(proposal.frequency()))
                .currency(defaultIfBlank(proposal.currency(), "USD"))
                .startDate(startDate)
                .endDate(parseOptionalDate(proposal.endDate()))
                .nextDueDate(startDate)
                .isActive(true)
                .notificationEnabled(proposal.notificationEnabled() == null || proposal.notificationEnabled())
                .notificationDaysBefore(defaultNotificationDays(proposal.notificationDaysBefore()))
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .syncStatus("pending")
                .build();

        recurringExpenseRepository.save(recurring);
    }

    private CategoryEntity resolveOrCreateCategory(String firebaseUid, String name, CategoryType categoryType) {
        return resolveOrCreateCategory(firebaseUid, name, categoryType, "tag", "#6366F1");
    }

    private CategoryEntity resolveOrCreateCategory(
            String firebaseUid,
            String name,
            CategoryType categoryType,
            String icon,
            String color) {
        List<CategoryEntity> categories = categoryService.getCategoriesByType(firebaseUid, categoryType);
        String requestedName = name.trim().toLowerCase(Locale.ROOT);
        for (CategoryEntity category : categories) {
            if (category.getName() != null
                    && category.getName().trim().toLowerCase(Locale.ROOT).equals(requestedName)) {
                return category;
            }
        }
        log.info("Creating AI category '{}' ({}) for user {}", name, categoryType, firebaseUid);
        return categoryService.createCategory(firebaseUid, name.trim(), icon, color, categoryType);
    }

    private String normalizeBudgetMonth(String rawMonth) {
        if (rawMonth == null || rawMonth.isBlank()) {
            return YearMonth.now(ZoneOffset.UTC).toString();
        }
        try {
            return YearMonth.parse(rawMonth.trim()).toString();
        } catch (DateTimeParseException ex) {
            throw AppException.badRequest("Budget month must use YYYY-MM format");
        }
    }

    private CategoryType parseCategoryType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return CategoryType.EXPENSE;
        }
        return "income".equalsIgnoreCase(rawType) ? CategoryType.INCOME : CategoryType.EXPENSE;
    }

    private String normalizeTransactionType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "EXPENSE";
        }
        return "income".equalsIgnoreCase(rawType) ? "INCOME" : "EXPENSE";
    }

    private String normalizeFrequency(String rawFrequency) {
        if (rawFrequency == null || rawFrequency.isBlank()) {
            return "monthly";
        }
        String normalized = rawFrequency.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "daily", "weekly", "biweekly", "monthly", "yearly" -> normalized;
            default -> throw AppException.badRequest("Recurring frequency is invalid");
        };
    }

    private OffsetDateTime parseRequiredDate(String raw, String errorMessage) {
        OffsetDateTime parsed = parseOptionalDate(raw);
        if (parsed == null) {
            throw AppException.badRequest(errorMessage);
        }
        return parsed;
    }

    private OffsetDateTime parseOptionalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim()).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(raw.trim()).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        throw AppException.badRequest("Date value must be an ISO date or datetime");
    }

    private int defaultNotificationDays(Integer raw) {
        if (raw == null || raw < 0) {
            return 1;
        }
        return raw;
    }

    private Double defaultIfNull(Double value, Double fallback) {
        return value == null ? fallback : value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullable(String value) {
        return normalizeText(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }
}
