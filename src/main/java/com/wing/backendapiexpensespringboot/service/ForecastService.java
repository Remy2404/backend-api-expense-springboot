package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ForecastResponse;
import com.wing.backendapiexpensespringboot.dto.ForecastRiskCategory;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final ExpenseService expenseService;

    public ForecastResponse getForecast(String firebaseUid) {
        log.info("Getting forecast for user: {}", firebaseUid);

        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate thirtyDaysAgo = today.minusDays(30);
        LocalDate ninetyDaysAgo = today.minusDays(90);

        // Get current month expenses
        List<ExpenseEntity> currentMonth = expenseService.getExpensesBetween(firebaseUid, startOfMonth, today);
        double currentMonthTotal = currentMonth.stream().mapToDouble(ExpenseEntity::getAmount).sum();

        // Get last 30 days
        List<ExpenseEntity> last30Days = expenseService.getExpensesBetween(firebaseUid, thirtyDaysAgo, today);
        double last30DaysTotal = last30Days.stream().mapToDouble(ExpenseEntity::getAmount).sum();

        // Get last 90 days
        List<ExpenseEntity> last90Days = expenseService.getExpensesBetween(firebaseUid, ninetyDaysAgo, today);

        // Estimate monthly total
        double dailyAverage = last30DaysTotal / 30.0;
        int daysInMonth = today.lengthOfMonth();
        int remainingDays = daysInMonth - today.getDayOfMonth();
        double estimatedMonthTotal = currentMonthTotal + (dailyAverage * remainingDays);

        // Estimate savings (assuming budget of $2000)
        double budget = 2000.0;
        double estimatedSavings = Math.max(0, budget - estimatedMonthTotal);

        // Determine data confidence
        String dataConfidence;
        if (last90Days.size() < 7) {
            dataConfidence = "insufficient";
        } else if (last90Days.size() < 30) {
            dataConfidence = "low";
        } else if (last90Days.size() < 60) {
            dataConfidence = "medium";
        } else {
            dataConfidence = "high";
        }

        // Risk categories
        List<ForecastRiskCategory> riskCategories = new ArrayList<>();
        if (estimatedMonthTotal > budget * 0.9) {
            riskCategories.add(ForecastRiskCategory.builder()
                    .category("Overall")
                    .reason("Approaching monthly budget limit")
                    .build());
        }

        return ForecastResponse.builder()
                .estimatedMonthTotal(estimatedMonthTotal)
                .estimatedSavings(estimatedSavings)
                .riskCategories(riskCategories)
                .dataConfidence(dataConfidence)
                .daysOfData(last90Days.size())
                .confidence(0.7)
                .disclaimer("Forecast is based on historical data and may not reflect future spending.")
                .needsConfirmation(false)
                .safetyWarnings(new ArrayList<>())
                .build();
    }
}
