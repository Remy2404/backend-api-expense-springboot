package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.NudgeActionItem;
import com.wing.backendapiexpensespringboot.dto.NudgeItem;
import com.wing.backendapiexpensespringboot.dto.NudgesResponse;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryBudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.model.RecurringExpenseEntity;
import com.wing.backendapiexpensespringboot.model.SavingsGoalEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryBudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.RecurringExpenseRepository;
import com.wing.backendapiexpensespringboot.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NudgeService {

    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final double BUDGET_NEARLY_REACHED_THRESHOLD = 0.9d;
    private static final double WEEKLY_SPIKE_THRESHOLD = 1.35d;
    private static final double CATEGORY_SPIKE_THRESHOLD = 1.5d;
    private static final int MAX_NUDGES = 5;
    private static final int RECURRING_LOOKBACK_DAYS = 120;
    private static final int WEEKLY_BASELINE_WEEKS = 6;

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;
    private final CategoryRepository categoryRepository;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final SavingsGoalRepository savingsGoalRepository;

    public NudgesResponse getNudges(String firebaseUid) {
        log.info("Getting nudges for user: {}", firebaseUid);

        OffsetDateTime generatedAt = OffsetDateTime.now(UTC);
        LocalDate today = generatedAt.toLocalDate();
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth previousMonth = currentMonth.minusMonths(1);
        OffsetDateTime currentMonthStart = startOfMonth(currentMonth);
        OffsetDateTime currentMonthEndExclusive = startOfMonth(currentMonth.plusMonths(1));
        OffsetDateTime previousMonthStart = startOfMonth(previousMonth);
        OffsetDateTime previousMonthEndExclusive = currentMonthStart;

        List<CategoryEntity> categories = categoryRepository.findActiveByFirebaseUidOrderByNameAsc(firebaseUid);
        Map<UUID, CategoryEntity> categoriesById = categories.stream()
                .collect(Collectors.toMap(CategoryEntity::getId, category -> category));

        List<ExpenseEntity> currentMonthExpenses = loadActiveExpenses(firebaseUid, currentMonthStart, currentMonthEndExclusive);
        List<ExpenseEntity> previousMonthExpenses = loadActiveExpenses(firebaseUid, previousMonthStart, previousMonthEndExclusive);
        LocalDate currentWeekStartDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        OffsetDateTime currentWeekStart = startOfDay(currentWeekStartDate);
        OffsetDateTime currentWeekEndExclusive = currentWeekStart.plusDays(7);
        OffsetDateTime weeklyBaselineStart = currentWeekStart.minusWeeks(WEEKLY_BASELINE_WEEKS);
        List<ExpenseEntity> currentWeekExpenses = loadActiveExpenses(firebaseUid, currentWeekStart, currentWeekEndExclusive);
        List<ExpenseEntity> historicalWeeklyExpenses = loadActiveExpenses(firebaseUid, weeklyBaselineStart, currentWeekStart);
        List<ExpenseEntity> recurringDetectionExpenses = loadActiveExpenses(
                firebaseUid,
                startOfDay(today.minusDays(RECURRING_LOOKBACK_DAYS)),
                currentMonthEndExclusive
        );
        List<RecurringExpenseEntity> recurringExpenses =
                recurringExpenseRepository.findActiveByFirebaseUidOrderByNextDueDateAsc(firebaseUid);
        List<SavingsGoalEntity> goals = savingsGoalRepository.findActiveByFirebaseUidOrderByCreatedAtDesc(firebaseUid);

        Optional<BudgetEntity> currentBudget = budgetRepository.findActiveByMonthAndFirebaseUid(
                currentMonth.toString(),
                firebaseUid
        );
        List<CategoryBudgetEntity> currentCategoryBudgets = currentBudget
                .map(BudgetEntity::getId)
                .map((budgetId) -> categoryBudgetRepository.findByBudgetIdAndFirebaseUid(budgetId, firebaseUid))
                .orElseGet(List::of);

        RecurringPatternCandidate recurringPattern = detectRecurringPattern(
                recurringDetectionExpenses,
                recurringExpenses
        );

        List<NudgeCandidate> candidates = new ArrayList<>();
        addCandidate(candidates, buildBudgetExceededInsight(
                currentBudget,
                currentCategoryBudgets,
                currentMonthExpenses,
                categoriesById,
                generatedAt
        ));
        addCandidate(candidates, buildBudgetNearlyReachedInsight(
                currentBudget,
                currentCategoryBudgets,
                currentMonthExpenses,
                categoriesById,
                generatedAt
        ));
        addCandidate(candidates, buildUnusualSpendingInsight(
                currentWeekExpenses,
                historicalWeeklyExpenses,
                generatedAt
        ));
        addCandidate(candidates, buildRecurringSubscriptionInsight(recurringPattern, generatedAt));
        addCandidate(candidates, buildSavingsOpportunityInsight(
                recurringPattern,
                currentMonthExpenses,
                previousMonthExpenses,
                categoriesById,
                goals,
                generatedAt
        ));
        addCandidate(candidates, buildIncomeChangeInsight(
                currentMonthExpenses,
                previousMonthExpenses,
                goals,
                generatedAt
        ));
        addCandidate(candidates, buildCategoryOverspendingInsight(
                currentMonthExpenses,
                previousMonthExpenses,
                categoriesById,
                generatedAt
        ));

        List<NudgeItem> nudges = candidates.stream()
                .sorted(Comparator
                        .comparingInt(NudgeCandidate::priority)
                        .thenComparing(NudgeCandidate::score, Comparator.reverseOrder()))
                .limit(MAX_NUDGES)
                .map(NudgeCandidate::item)
                .toList();

        return NudgesResponse.builder()
                .nudges(nudges)
                .generatedAt(generatedAt)
                .needsConfirmation(false)
                .safetyWarnings(List.of())
                .build();
    }

    private void addCandidate(List<NudgeCandidate> candidates, Optional<NudgeCandidate> candidate) {
        candidate.ifPresent(candidates::add);
    }

    private Optional<NudgeCandidate> buildBudgetExceededInsight(
            Optional<BudgetEntity> currentBudget,
            List<CategoryBudgetEntity> categoryBudgets,
            List<ExpenseEntity> currentMonthExpenses,
            Map<UUID, CategoryEntity> categoriesById,
            OffsetDateTime generatedAt
    ) {
        Map<UUID, Double> currentMonthExpenseTotals = sumExpenseTotalsByCategory(currentMonthExpenses);

        CategoryBudgetExceeded categoryExceeded = categoryBudgets.stream()
                .filter((entry) -> entry.getCategoryId() != null)
                .map((entry) -> {
                    double limit = toDouble(entry.getAmount());
                    double spent = currentMonthExpenseTotals.getOrDefault(entry.getCategoryId(), 0.0d);
                    double overBy = spent - limit;
                    return new CategoryBudgetExceeded(entry, spent, limit, overBy);
                })
                .filter((entry) -> entry.overBy() > 0.01d)
                .max(Comparator.comparingDouble(CategoryBudgetExceeded::overBy))
                .orElse(null);

        if (categoryExceeded != null) {
            String categoryName = categoryNameFor(categoriesById, categoryExceeded.entry().getCategoryId());
            return Optional.of(new NudgeCandidate(
                    1,
                    categoryExceeded.overBy(),
                    NudgeItem.builder()
                            .id("budget_exceeded_" + slugify(categoryName))
                            .type("budget_exceeded")
                            .title("Budget exceeded")
                            .body(String.format(
                                    Locale.US,
                                    "You exceeded your %s budget by %s this month.",
                                    categoryName,
                                    formatMoney(categoryExceeded.overBy())
                            ))
                            .category(categoryName)
                            .actions(List.of(
                                    action("edit-budget", "Adjust budget", "edit_budget"),
                                    action("view-transactions", "View transactions", "view_transactions")
                            ))
                            .severity("critical")
                            .generatedAt(generatedAt)
                            .build()
            ));
        }

        BudgetEntity budget = currentBudget.orElse(null);
        if (budget == null) {
            return Optional.empty();
        }

        double totalLimit = toDouble(budget.getTotalAmount());
        if (totalLimit <= 0.0d) {
            return Optional.empty();
        }

        double totalSpent = sumExpenseAmounts(currentMonthExpenses);
        double overBy = totalSpent - totalLimit;
        if (overBy <= 0.01d) {
            return Optional.empty();
        }

        return Optional.of(new NudgeCandidate(
                1,
                overBy,
                NudgeItem.builder()
                        .id("budget_exceeded_monthly")
                        .type("budget_exceeded")
                        .title("Budget exceeded")
                        .body(String.format(
                                Locale.US,
                                "You exceeded your monthly budget by %s this month.",
                                formatMoney(overBy)
                        ))
                        .actions(List.of(
                                action("edit-budget", "Adjust budget", "edit_budget"),
                                action("view-transactions", "View transactions", "view_transactions")
                        ))
                        .severity("critical")
                        .generatedAt(generatedAt)
                        .build()
        ));
    }

    private Optional<NudgeCandidate> buildBudgetNearlyReachedInsight(
            Optional<BudgetEntity> currentBudget,
            List<CategoryBudgetEntity> categoryBudgets,
            List<ExpenseEntity> currentMonthExpenses,
            Map<UUID, CategoryEntity> categoriesById,
            OffsetDateTime generatedAt
    ) {
        Map<UUID, Double> currentMonthExpenseTotals = sumExpenseTotalsByCategory(currentMonthExpenses);

        CategoryBudgetProgress categoryProgress = categoryBudgets.stream()
                .filter((entry) -> entry.getCategoryId() != null)
                .map((entry) -> {
                    double limit = toDouble(entry.getAmount());
                    double spent = currentMonthExpenseTotals.getOrDefault(entry.getCategoryId(), 0.0d);
                    double ratio = limit > 0.0d ? spent / limit : 0.0d;
                    return new CategoryBudgetProgress(entry, spent, limit, ratio);
                })
                .filter((entry) ->
                        entry.limit() > 0.0d &&
                                entry.spent() <= entry.limit() &&
                                entry.ratio() >= BUDGET_NEARLY_REACHED_THRESHOLD)
                .max(Comparator.comparingDouble(CategoryBudgetProgress::ratio))
                .orElse(null);

        if (categoryProgress != null) {
            String categoryName = categoryNameFor(categoriesById, categoryProgress.entry().getCategoryId());
            return Optional.of(new NudgeCandidate(
                    2,
                    categoryProgress.ratio(),
                    NudgeItem.builder()
                            .id("budget_nearly_reached_" + slugify(categoryName))
                            .type("budget_nearly_reached")
                            .title("Budget nearly reached")
                            .body(String.format(
                                    Locale.US,
                                    "You already spent %s of your %s %s budget.",
                                    formatMoney(categoryProgress.spent()),
                                    formatMoney(categoryProgress.limit()),
                                    categoryName
                            ))
                            .category(categoryName)
                            .actions(List.of(
                                    action("view-transactions", "View transactions", "view_transactions"),
                                    action("increase-budget", "Increase budget", "increase_budget")
                            ))
                            .severity("warning")
                            .generatedAt(generatedAt)
                            .build()
            ));
        }

        BudgetEntity budget = currentBudget.orElse(null);
        if (budget == null) {
            return Optional.empty();
        }

        double totalLimit = toDouble(budget.getTotalAmount());
        if (totalLimit <= 0.0d) {
            return Optional.empty();
        }

        double totalSpent = sumExpenseAmounts(currentMonthExpenses);
        double ratio = totalSpent / totalLimit;
        if (totalSpent > totalLimit || ratio < BUDGET_NEARLY_REACHED_THRESHOLD) {
            return Optional.empty();
        }

        return Optional.of(new NudgeCandidate(
                2,
                ratio,
                NudgeItem.builder()
                        .id("budget_nearly_reached_monthly")
                        .type("budget_nearly_reached")
                        .title("Budget nearly reached")
                        .body(String.format(
                                Locale.US,
                                "You already spent %s of your %s monthly budget.",
                                formatMoney(totalSpent),
                                formatMoney(totalLimit)
                        ))
                        .actions(List.of(
                                action("view-transactions", "View transactions", "view_transactions"),
                                action("increase-budget", "Increase budget", "increase_budget")
                        ))
                        .severity("warning")
                        .generatedAt(generatedAt)
                        .build()
        ));
    }

    private Optional<NudgeCandidate> buildUnusualSpendingInsight(
            List<ExpenseEntity> currentWeekExpenses,
            List<ExpenseEntity> historicalWeeklyExpenses,
            OffsetDateTime generatedAt
    ) {
        double currentWeekTotal = sumExpenseAmounts(currentWeekExpenses);
        if (currentWeekTotal <= 0.0d) {
            return Optional.empty();
        }

        Map<LocalDate, Double> weeklyTotals = new HashMap<>();
        for (ExpenseEntity expense : historicalWeeklyExpenses) {
            if (!isExpenseTransaction(expense) || expense.getDate() == null) {
                continue;
            }
            LocalDate weekStart = expense.getDate()
                    .withOffsetSameInstant(UTC)
                    .toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weeklyTotals.merge(weekStart, expense.getAmount(), Double::sum);
        }

        if (weeklyTotals.size() < 3) {
            return Optional.empty();
        }

        double averageWeeklySpend = weeklyTotals.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0d);
        if (averageWeeklySpend <= 0.0d || currentWeekTotal < averageWeeklySpend * WEEKLY_SPIKE_THRESHOLD) {
            return Optional.empty();
        }

        double increasePct = ((currentWeekTotal - averageWeeklySpend) / averageWeeklySpend) * 100.0d;
        return Optional.of(new NudgeCandidate(
                3,
                increasePct,
                NudgeItem.builder()
                        .id("unusual_spending_weekly")
                        .type("unusual_spending")
                        .title("Unusual spending detected")
                        .body(String.format(
                                Locale.US,
                                "Your spending this week is %.0f%% higher than your weekly average.",
                                increasePct
                        ))
                        .actions(List.of(
                                action("view-transactions", "View transactions", "view_transactions"),
                                action("review-categories", "Review categories", "review_categories")
                        ))
                        .severity("warning")
                        .generatedAt(generatedAt)
                        .build()
        ));
    }

    private Optional<NudgeCandidate> buildRecurringSubscriptionInsight(
            RecurringPatternCandidate recurringPattern,
            OffsetDateTime generatedAt
    ) {
        if (recurringPattern.primaryMerchant() == null || recurringPattern.primaryAmount() <= 0.0d) {
            return Optional.empty();
        }

        return Optional.of(new NudgeCandidate(
                4,
                recurringPattern.primaryAmount(),
                NudgeItem.builder()
                        .id("recurring_subscription_" + slugify(recurringPattern.primaryMerchant()))
                        .type("recurring_subscription")
                        .title("Recurring payment detected")
                        .body(String.format(
                                Locale.US,
                                "A recurring payment of %s to %s was detected.",
                                formatMoney(recurringPattern.primaryAmount()),
                                recurringPattern.primaryMerchant()
                        ))
                        .category(recurringPattern.primaryMerchant())
                        .actions(List.of(
                                action("create-recurring", "Create recurring expense", "create_recurring_expense"),
                                action("ignore-suggestion", "Ignore suggestion", "ignore_suggestion")
                        ))
                        .severity("info")
                        .generatedAt(generatedAt)
                        .build()
        ));
    }

    private Optional<NudgeCandidate> buildSavingsOpportunityInsight(
            RecurringPatternCandidate recurringPattern,
            List<ExpenseEntity> currentMonthExpenses,
            List<ExpenseEntity> previousMonthExpenses,
            Map<UUID, CategoryEntity> categoriesById,
            List<SavingsGoalEntity> goals,
            OffsetDateTime generatedAt
    ) {
        double recurringOpportunity = recurringPattern.monthlyOpportunityAmount();
        if (recurringOpportunity >= 20.0d) {
            return Optional.of(new NudgeCandidate(
                    5,
                    recurringOpportunity,
                    NudgeItem.builder()
                            .id("savings_opportunity_recurring")
                            .type("savings_opportunity")
                            .title("Savings opportunity")
                            .body(String.format(
                                    Locale.US,
                                    "You could save %s per month by reviewing repeating charges in your spending.",
                                    formatMoney(recurringOpportunity)
                            ))
                            .actions(List.of(
                                    savingsAction(goals),
                                    action("allocate-savings", "Allocate to savings", "allocate_to_savings")
                            ))
                            .severity("info")
                            .generatedAt(generatedAt)
                            .build()
            ));
        }

        CategoryGrowth categoryGrowth = findStrongestCategoryGrowth(currentMonthExpenses, previousMonthExpenses);
        if (categoryGrowth == null || categoryGrowth.deltaAmount() < 40.0d) {
            return Optional.empty();
        }

        String categoryName = categoryNameFor(categoriesById, categoryGrowth.categoryId());
        return Optional.of(new NudgeCandidate(
                5,
                categoryGrowth.deltaAmount(),
                NudgeItem.builder()
                        .id("savings_opportunity_" + slugify(categoryName))
                        .type("savings_opportunity")
                        .title("Savings opportunity")
                        .body(String.format(
                                Locale.US,
                                "You could save about %s next month by trimming your %s spending back to last month’s level.",
                                formatMoney(categoryGrowth.deltaAmount()),
                                categoryName
                        ))
                        .category(categoryName)
                        .actions(List.of(
                                savingsAction(goals),
                                action("allocate-savings", "Allocate to savings", "allocate_to_savings")
                        ))
                        .severity("info")
                        .generatedAt(generatedAt)
                        .build()
        ));
    }

    private Optional<NudgeCandidate> buildIncomeChangeInsight(
            List<ExpenseEntity> currentMonthExpenses,
            List<ExpenseEntity> previousMonthExpenses,
            List<SavingsGoalEntity> goals,
            OffsetDateTime generatedAt
    ) {
        double currentMonthIncome = sumIncomeAmounts(currentMonthExpenses);
        double previousMonthIncome = sumIncomeAmounts(previousMonthExpenses);
        double delta = currentMonthIncome - previousMonthIncome;

        if (Math.abs(delta) < 100.0d || (currentMonthIncome <= 0.0d && previousMonthIncome <= 0.0d)) {
            return Optional.empty();
        }

        boolean increase = delta >= 0.0d;
        String body = previousMonthIncome > 0.0d
                ? String.format(
                        Locale.US,
                        "Your income this month %s by %s compared to last month.",
                        increase ? "increased" : "decreased",
                        formatMoney(Math.abs(delta))
                )
                : String.format(
                        Locale.US,
                        "You recorded %s in income this month. Consider assigning it intentionally.",
                        formatMoney(currentMonthIncome)
                );

        return Optional.of(new NudgeCandidate(
                6,
                Math.abs(delta),
                NudgeItem.builder()
                        .id("income_change_" + (increase ? "up" : "down"))
                        .type("income_change")
                        .title(increase ? "Income increased" : "Income changed")
                        .body(body)
                        .actions(List.of(
                                action("allocate-savings", "Allocate to savings", "allocate_to_savings"),
                                savingsAction(goals)
                        ))
                        .severity(increase ? "info" : "warning")
                        .generatedAt(generatedAt)
                        .build()
        ));
    }

    private Optional<NudgeCandidate> buildCategoryOverspendingInsight(
            List<ExpenseEntity> currentMonthExpenses,
            List<ExpenseEntity> previousMonthExpenses,
            Map<UUID, CategoryEntity> categoriesById,
            OffsetDateTime generatedAt
    ) {
        CategoryGrowth categoryGrowth = findStrongestCategoryGrowth(currentMonthExpenses, previousMonthExpenses);
        if (categoryGrowth == null || categoryGrowth.previousTotal() <= 0.0d) {
            return Optional.empty();
        }

        double increasePct = ((categoryGrowth.currentTotal() - categoryGrowth.previousTotal())
                / categoryGrowth.previousTotal()) * 100.0d;
        if (increasePct < 30.0d) {
            return Optional.empty();
        }

        String categoryName = categoryNameFor(categoriesById, categoryGrowth.categoryId());
        return Optional.of(new NudgeCandidate(
                7,
                increasePct,
                NudgeItem.builder()
                        .id("category_overspending_" + slugify(categoryName))
                        .type("category_overspending")
                        .title("Category spending increased")
                        .body(String.format(
                                Locale.US,
                                "Your %s spending increased by %.0f%% compared to last month.",
                                categoryName,
                                increasePct
                        ))
                        .category(categoryName)
                        .actions(List.of(
                                action("view-transactions", "Review transactions", "view_transactions"),
                                action("adjust-category-budget", "Adjust category budget", "adjust_category_budget")
                        ))
                        .severity("warning")
                        .generatedAt(generatedAt)
                        .build()
        ));
    }

    private RecurringPatternCandidate detectRecurringPattern(
            List<ExpenseEntity> expenses,
            List<RecurringExpenseEntity> recurringExpenses
    ) {
        Map<String, List<ExpenseEntity>> expensesByMerchant = expenses.stream()
                .filter(this::isExpenseTransaction)
                .filter((expense) -> expense.getRecurringExpenseId() == null)
                .filter((expense) -> expense.getMerchant() != null && !expense.getMerchant().isBlank())
                .collect(Collectors.groupingBy((expense) -> normalizeMerchant(expense.getMerchant())));

        double totalOpportunity = 0.0d;
        MerchantPattern strongestPattern = null;

        for (Map.Entry<String, List<ExpenseEntity>> entry : expensesByMerchant.entrySet()) {
            String merchantKey = entry.getKey();
            if (merchantKey.isBlank() || merchantAlreadyTracked(merchantKey, recurringExpenses)) {
                continue;
            }

            MerchantPattern pattern = toMerchantPattern(entry.getValue());
            if (pattern == null) {
                continue;
            }

            totalOpportunity += pattern.averageAmount();
            if (strongestPattern == null || pattern.averageAmount() > strongestPattern.averageAmount()) {
                strongestPattern = pattern;
            }
        }

        if (strongestPattern == null) {
            return new RecurringPatternCandidate(null, 0.0d, totalOpportunity);
        }

        return new RecurringPatternCandidate(
                strongestPattern.displayMerchant(),
                strongestPattern.averageAmount(),
                totalOpportunity
        );
    }

    private MerchantPattern toMerchantPattern(List<ExpenseEntity> expenses) {
        if (expenses.size() < 2) {
            return null;
        }

        List<ExpenseEntity> sorted = expenses.stream()
                .sorted(Comparator.comparing(ExpenseEntity::getDate))
                .toList();
        List<Long> intervals = new ArrayList<>();
        for (int index = 1; index < sorted.size(); index++) {
            long days = sorted.get(index).getDate()
                    .toLocalDate()
                    .toEpochDay() - sorted.get(index - 1).getDate().toLocalDate().toEpochDay();
            intervals.add(days);
        }

        boolean monthlyCadence = intervals.stream().anyMatch((days) -> days >= 21 && days <= 40);
        if (!monthlyCadence) {
            return null;
        }

        double averageAmount = sorted.stream().mapToDouble(ExpenseEntity::getAmount).average().orElse(0.0d);
        if (averageAmount <= 0.0d) {
            return null;
        }

        double maxDeviation = sorted.stream()
                .mapToDouble((expense) -> Math.abs(expense.getAmount() - averageAmount))
                .max()
                .orElse(averageAmount);
        if (maxDeviation / averageAmount > 0.2d) {
            return null;
        }

        return new MerchantPattern(sorted.get(sorted.size() - 1).getMerchant(), averageAmount);
    }

    private boolean merchantAlreadyTracked(String merchantKey, List<RecurringExpenseEntity> recurringExpenses) {
        return recurringExpenses.stream().anyMatch((expense) -> {
            String notes = expense.getNotes();
            return notes != null && normalizeMerchant(notes).contains(merchantKey);
        });
    }

    private CategoryGrowth findStrongestCategoryGrowth(
            List<ExpenseEntity> currentMonthExpenses,
            List<ExpenseEntity> previousMonthExpenses
    ) {
        Map<UUID, Double> currentTotals = sumExpenseTotalsByCategory(currentMonthExpenses);
        Map<UUID, Double> previousTotals = sumExpenseTotalsByCategory(previousMonthExpenses);

        return currentTotals.entrySet().stream()
                .filter((entry) -> entry.getKey() != null)
                .map((entry) -> {
                    double previous = previousTotals.getOrDefault(entry.getKey(), 0.0d);
                    double current = entry.getValue();
                    double delta = current - previous;
                    return new CategoryGrowth(entry.getKey(), current, previous, delta);
                })
                .filter((growth) ->
                        growth.previousTotal() > 0.0d &&
                                growth.currentTotal() >= growth.previousTotal() * CATEGORY_SPIKE_THRESHOLD &&
                                growth.deltaAmount() >= 25.0d)
                .max(Comparator.comparingDouble(CategoryGrowth::deltaAmount))
                .orElse(null);
    }

    private Map<UUID, Double> sumExpenseTotalsByCategory(List<ExpenseEntity> expenses) {
        Map<UUID, Double> totals = new HashMap<>();
        for (ExpenseEntity expense : expenses) {
            if (!isExpenseTransaction(expense) || expense.getCategoryId() == null) {
                continue;
            }
            totals.merge(expense.getCategoryId(), expense.getAmount(), Double::sum);
        }
        return totals;
    }

    private List<ExpenseEntity> loadActiveExpenses(
            String firebaseUid,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive
    ) {
        return expenseRepository.findByFirebaseUidAndDateBetweenOrderByDateDesc(firebaseUid, startInclusive, endExclusive)
                .stream()
                .filter((expense) -> !Boolean.TRUE.equals(expense.getIsDeleted()))
                .toList();
    }

    private boolean isExpenseTransaction(ExpenseEntity expense) {
        return expense.getAmount() != null &&
                expense.getAmount() > 0.0d &&
                !"INCOME".equalsIgnoreCase(expense.getTransactionType());
    }

    private double sumExpenseAmounts(List<ExpenseEntity> expenses) {
        return expenses.stream()
                .filter(this::isExpenseTransaction)
                .mapToDouble(ExpenseEntity::getAmount)
                .sum();
    }

    private double sumIncomeAmounts(List<ExpenseEntity> expenses) {
        return expenses.stream()
                .filter((expense) ->
                        expense.getAmount() != null &&
                                expense.getAmount() > 0.0d &&
                                "INCOME".equalsIgnoreCase(expense.getTransactionType()) &&
                                !Boolean.TRUE.equals(expense.getIsDeleted()))
                .mapToDouble(ExpenseEntity::getAmount)
                .sum();
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0.0d : value.doubleValue();
    }

    private OffsetDateTime startOfMonth(YearMonth yearMonth) {
        return yearMonth.atDay(1).atStartOfDay().atOffset(UTC);
    }

    private OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay().atOffset(UTC);
    }

    private String formatMoney(double amount) {
        return String.format(Locale.US, "$%.2f", amount);
    }

    private String categoryNameFor(Map<UUID, CategoryEntity> categoriesById, UUID categoryId) {
        CategoryEntity category = categoriesById.get(categoryId);
        return category != null ? category.getName() : "category";
    }

    private String normalizeMerchant(String merchant) {
        return merchant == null ? "" : merchant.trim().toLowerCase(Locale.US);
    }

    private String slugify(String raw) {
        return raw == null
                ? "general"
                : raw.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private NudgeActionItem action(String id, String label, String action) {
        return NudgeActionItem.builder()
                .id(id)
                .label(label)
                .action(action)
                .build();
    }

    private NudgeActionItem savingsAction(List<SavingsGoalEntity> goals) {
        return goals.isEmpty()
                ? action("create-goal", "Create savings goal", "create_savings_goal")
                : action("create-goal", "Create new goal", "create_savings_goal");
    }

    private record NudgeCandidate(int priority, double score, NudgeItem item) {
    }

    private record CategoryBudgetExceeded(
            CategoryBudgetEntity entry,
            double spent,
            double limit,
            double overBy
    ) {
    }

    private record CategoryBudgetProgress(
            CategoryBudgetEntity entry,
            double spent,
            double limit,
            double ratio
    ) {
    }

    private record CategoryGrowth(
            UUID categoryId,
            double currentTotal,
            double previousTotal,
            double deltaAmount
    ) {
    }

    private record MerchantPattern(String displayMerchant, double averageAmount) {
    }

    private record RecurringPatternCandidate(
            String primaryMerchant,
            double primaryAmount,
            double monthlyOpportunityAmount
    ) {
    }
}
