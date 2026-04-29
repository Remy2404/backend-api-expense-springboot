package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.DashboardSummaryResponseDto;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryRepository;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    private final ExpenseRepository expenseRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponseDto getSummary(String firebaseUid) {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        OffsetDateTime monthStart = firstDayOfCurrentMonthUtc();
        OffsetDateTime monthEndExclusive = firstDayOfNextMonthUtc();

        ExpenseRepository.FinanceSummaryAggregate allTimeAggregate = expenseRepository.summarizeByFirebaseUid(firebaseUid);
        ExpenseRepository.FinanceSummaryAggregate currentMonthAggregate = expenseRepository
                .summarizeByFirebaseUidAndDateBetween(
                        firebaseUid,
                        monthStart,
                        monthEndExclusive);

        double totalIncome = toDouble(allTimeAggregate == null ? null : allTimeAggregate.getTotalIncome());
        double totalExpense = toDouble(allTimeAggregate == null ? null : allTimeAggregate.getTotalExpense());
        long transactionCount = toLong(allTimeAggregate == null ? null : allTimeAggregate.getTransactionCount());
        double monthlyIncome = toDouble(currentMonthAggregate == null ? null : currentMonthAggregate.getTotalIncome());
        double monthlyExpense = toDouble(currentMonthAggregate == null ? null : currentMonthAggregate.getTotalExpense());
        double currentBudgetLimit = budgetRepository
                .findActiveByMonthAndFirebaseUid(currentMonth.toString(), firebaseUid)
                .map(BudgetEntity::getTotalAmount)
                .map(Number::doubleValue)
                .orElse(0.0d);

        List<ExpenseEntity> recentExpenses = expenseRepository.findActiveByFirebaseUidOrderByDateDesc(
                firebaseUid,
                PageRequest.of(0, 5));
        Set<UUID> categoryIds = recentExpenses.stream()
                .map(ExpenseEntity::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> categoryNameById = categoryIds.isEmpty()
                ? Map.of()
                : categoryRepository.findActiveByFirebaseUidAndIdIn(firebaseUid, categoryIds).stream()
                        .collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getName));

        List<DashboardSummaryResponseDto.RecentTransactionItem> recentTransactions = recentExpenses.stream()
                .map((expense) -> DashboardSummaryResponseDto.RecentTransactionItem.builder()
                        .id(expense.getId().toString())
                        .amount(expense.getAmount())
                        .transactionType(expense.getTransactionType())
                        .currency(expense.getCurrency())
                        .merchant(expense.getMerchant())
                        .date(expense.getDate() == null ? null : expense.getDate().withOffsetSameInstant(ZoneOffset.UTC).toString())
                        .note(expense.getNote())
                        .noteSummary(expense.getNoteSummary())
                        .categoryId(expense.getCategoryId() == null ? null : expense.getCategoryId().toString())
                        .isDeleted(Boolean.TRUE.equals(expense.getIsDeleted()))
                        .categoryName(expense.getCategoryId() == null ? null : categoryNameById.get(expense.getCategoryId()))
                        .build())
                .toList();

        List<DashboardSummaryResponseDto.RecentCategoryItem> recentCategories = categoryNameById.entrySet().stream()
                .map((entry) -> DashboardSummaryResponseDto.RecentCategoryItem.builder()
                        .id(entry.getKey().toString())
                        .name(entry.getValue())
                        .build())
                .sorted(java.util.Comparator.comparing(DashboardSummaryResponseDto.RecentCategoryItem::getName))
                .toList();

        return DashboardSummaryResponseDto.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(totalIncome - totalExpense)
                .transactionCount(transactionCount)
                .monthlyIncome(monthlyIncome)
                .monthlyExpense(monthlyExpense)
                .budgetSummary(DashboardSummaryResponseDto.BudgetSummarySnapshot.builder()
                        .month(currentMonth.toString())
                        .budgetLimit(currentBudgetLimit)
                        .spent(monthlyExpense)
                        .remaining(currentBudgetLimit - monthlyExpense)
                        .build())
                .recentTransactions(recentTransactions)
                .recentCategories(recentCategories)
                .build();
    }

    private double toDouble(Number value) {
        if (value == null) {
            return 0.0d;
        }
        return value.doubleValue();
    }

    private long toLong(Number value) {
        if (value == null) {
            return 0L;
        }
        return value.longValue();
    }

    private OffsetDateTime firstDayOfCurrentMonthUtc() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return now.withDayOfMonth(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime firstDayOfNextMonthUtc() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return now.withDayOfMonth(1).plusMonths(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
