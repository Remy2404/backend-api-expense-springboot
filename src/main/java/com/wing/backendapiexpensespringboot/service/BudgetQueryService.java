package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.BudgetDto;
import com.wing.backendapiexpensespringboot.dto.CategoryBudgetDto;
import com.wing.backendapiexpensespringboot.model.BudgetEntity;
import com.wing.backendapiexpensespringboot.repository.BudgetRepository;
import com.wing.backendapiexpensespringboot.repository.CategoryBudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetQueryService {

    private final BudgetRepository budgetRepository;
    private final CategoryBudgetRepository categoryBudgetRepository;

    public List<BudgetDto> getBudgets(String firebaseUid, int offset, int limit, boolean includeArchived) {
        QueryPagination.validate(offset, limit);

        List<BudgetEntity> budgets = includeArchived
                ? budgetRepository.findAllByFirebaseUidOrderByMonthDesc(firebaseUid)
                : budgetRepository.findActiveByFirebaseUidOrderByMonthDesc(firebaseUid);
        List<BudgetEntity> pagedBudgets = QueryPagination.slice(budgets, offset, limit);

        Map<UUID, List<CategoryBudgetDto>> categoryBudgetsByBudgetId = pagedBudgets.stream()
                .collect(Collectors.toMap(
                        BudgetEntity::getId,
                        budget -> categoryBudgetRepository.findByBudgetIdAndFirebaseUid(
                                        budget.getId(), firebaseUid)
                                .stream()
                                .map(cb -> CategoryBudgetDto.builder()
                                        .id(cb.getId())
                                        .budgetId(cb.getBudgetId())
                                        .categoryId(cb.getCategoryId())
                                        .amount(cb.getAmount())
                                        .build())
                                .toList()));

        return pagedBudgets.stream().map(budget -> BudgetDto.builder()
                .id(budget.getId())
                .month(budget.getMonth())
                .totalAmount(budget.getTotalAmount())
                .categoryBudgets(categoryBudgetsByBudgetId.getOrDefault(budget.getId(), List.of()))
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build()).toList();
    }
}
