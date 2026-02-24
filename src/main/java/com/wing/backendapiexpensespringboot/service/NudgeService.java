package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.NudgeItem;
import com.wing.backendapiexpensespringboot.dto.NudgesResponse;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NudgeService {

    private final ExpenseService expenseService;

    public NudgesResponse getNudges(String firebaseUid) {
        log.info("Getting nudges for user: {}", firebaseUid);

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);

        List<ExpenseEntity> recentExpenses = expenseService.getExpensesBetween(firebaseUid, thirtyDaysAgo, today);
        List<NudgeItem> nudges = new ArrayList<>();

        // Calculate total spending
        double totalSpending = recentExpenses.stream().mapToDouble(ExpenseEntity::getAmount).sum();
        double dailyAverage = totalSpending / 30.0;

        // Budget risk nudge
        if (totalSpending > 1500) {
            nudges.add(NudgeItem.builder()
                    .id(UUID.randomUUID().toString())
                    .type("budget_risk")
                    .title("High Spending Alert")
                    .body(String.format("You've spent $%.2f in the last 30 days. Consider reviewing your budget.", totalSpending))
                    .actionPrompt("Review spending")
                    .severity("warning")
                    .generatedAt(LocalDateTime.now())
                    .build());
        }

        // Spending spike nudge
        double todaySpending = recentExpenses.stream()
                .filter(e -> e.getDate().equals(today))
                .mapToDouble(ExpenseEntity::getAmount)
                .sum();

        if (todaySpending > dailyAverage * 3) {
            nudges.add(NudgeItem.builder()
                    .id(UUID.randomUUID().toString())
                    .type("spending_spike")
                    .title("Spending Spike Detected")
                    .body(String.format("Your spending today ($%.2f) is much higher than your daily average ($%.2f).",
                            todaySpending, dailyAverage))
                    .severity("info")
                    .generatedAt(LocalDateTime.now())
                    .build());
        }

        // Limit nudges to 5
        if (nudges.size() > 5) {
            nudges = nudges.subList(0, 5);
        }

        return NudgesResponse.builder()
                .nudges(nudges)
                .needsConfirmation(false)
                .safetyWarnings(new ArrayList<>())
                .build();
    }
}
