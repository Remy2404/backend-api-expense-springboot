package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.*;
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
public class ScenarioService {

    private final ExpenseService expenseService;

    public ScenarioResponse runScenario(String firebaseUid, ScenarioRequest request) {
        log.info("Running scenario for user: {} - category: {}, delta: {}",
                firebaseUid, request.getCategory(), request.getDeltaAmount());

        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.minusDays(request.getPeriodDays());

        List<ExpenseEntity> periodExpenses = expenseService.getExpensesBetween(firebaseUid, periodStart, today);

        // Calculate baseline for the category
        double baselineTotal = periodExpenses.stream()
                .filter(e -> request.getCategory().equalsIgnoreCase(e.getMerchant()))
                .mapToDouble(ExpenseEntity::getAmount)
                .sum();

        // Calculate projected total
        double projectedTotal = baselineTotal + request.getDeltaAmount();

        // Determine data confidence
        String dataConfidence;
        if (periodExpenses.size() < 7) {
            dataConfidence = "insufficient";
        } else if (periodExpenses.size() < 30) {
            dataConfidence = "low";
        } else {
            dataConfidence = "medium";
        }

        ScenarioDelta delta = ScenarioDelta.builder()
                .category(request.getCategory())
                .baselineTotal(baselineTotal)
                .projectedTotal(projectedTotal)
                .delta(request.getDeltaAmount())
                .confidence(0.6)
                .build();

        return ScenarioResponse.builder()
                .scenarioLabel(String.format("What if you spend $%.2f more on %s?",
                        Math.abs(request.getDeltaAmount()), request.getCategory()))
                .deltas(List.of(delta))
                .projectedMonthTotal(projectedTotal)
                .projectedSavings(0.0)
                .dataConfidence(dataConfidence)
                .disclaimer("This is a hypothetical scenario based on historical data.")
                .needsConfirmation(false)
                .safetyWarnings(new ArrayList<>())
                .build();
    }
}
