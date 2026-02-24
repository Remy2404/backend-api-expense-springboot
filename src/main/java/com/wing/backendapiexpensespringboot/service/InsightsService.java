package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.InsightHighlight;
import com.wing.backendapiexpensespringboot.dto.InsightsResponse;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsService {

    private final ExpenseService expenseService;

    public InsightsResponse getInsights(String firebaseUid, String insightType) {
        log.info("Getting {} insights for user: {}", insightType, firebaseUid);

        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end = today;

        switch (insightType) {
            case "daily":
                start = today.minusDays(1);
                break;
            case "weekly":
                start = today.minusWeeks(1);
                break;
            case "monthly":
            default:
                start = today.minusMonths(1);
                break;
        }

        List<ExpenseEntity> expenses = expenseService.getExpensesBetween(firebaseUid, start, end);
        double total = expenses.stream().mapToDouble(ExpenseEntity::getAmount).sum();

        // Simple insights - in production this would use AI
        List<InsightHighlight> highlights = new ArrayList<>();
        Map<String, Double> categoryTotals = new HashMap<>();

        for (ExpenseEntity expense : expenses) {
            if (expense.getCategoryId() != null) {
                String catKey = expense.getCategoryId().toString();
                categoryTotals.put(catKey, categoryTotals.getOrDefault(catKey, 0.0) + expense.getAmount());
            }
        }

        // Find top category
        if (!categoryTotals.isEmpty()) {
            String topCategory = Collections.max(categoryTotals.entrySet(), Map.Entry.comparingByValue()).getKey();
            highlights.add(InsightHighlight.builder()
                    .category(topCategory)
                    .changePct(0.0)
                    .direction("up")
                    .build());
        }

        String summary = String.format("You spent $%.2f in the last %s. %d expenses recorded.",
                total, insightType, expenses.size());

        return InsightsResponse.builder()
                .insightType(insightType)
                .period(com.wing.backendapiexpensespringboot.dto.InsightPeriod.builder()
                        .start(start)
                        .end(end)
                        .build())
                .summary(summary)
                .highlights(highlights)
                .confidence(0.7)
                .needsConfirmation(false)
                .safetyWarnings(new ArrayList<>())
                .build();
    }
}
