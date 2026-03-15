package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import com.wing.backendapiexpensespringboot.dto.ChatRequest;
import com.wing.backendapiexpensespringboot.dto.ChatResponse;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOrchestratorServiceCreateIntentTest {

    @Mock
    private OpenRouterService openRouterService;
    @Mock
    private ExpenseService expenseService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private MemoryService memoryService;
    @Mock
    private CorrectionService correctionService;
    @Mock
    private SafetyValidatorService safetyValidatorService;
    @Mock
    private OpenRouterConfig openRouterConfig;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AiOrchestratorService aiOrchestratorService;

    private List<CategoryEntity> categories;

    @BeforeEach
    void setUp() throws Exception {
        categories = List.of(
                category("Food", "expense"),
                category("Shopping", "expense"),
                category("Transport", "expense"),
                category("Other", "expense"),
                category("Salary", "income"),
                category("Subscriptions", "expense")
        );

        when(expenseService.getExpensesBetween(anyString(), any(), any())).thenReturn(List.of());
        when(categoryService.getCategories(anyString())).thenReturn(categories);
        when(openRouterService.chat(anyString(), anyString())).thenReturn("{\"ok\":true}");
        when(objectMapper.readValue(anyString(), any(Class.class))).thenAnswer(invocation -> parsedResponse);
    }

    private Map<String, Object> parsedResponse;

    @Test
    void chat_shouldExtractBudgetPayload() {
        parsedResponse = Map.of(
                "intent", "add_budget",
                "confidence", 0.93,
                "payload", Map.of(
                        "kind", "budget",
                        "month", "2026-03",
                        "totalAmount", 500
                )
        );

        ChatResponse response = aiOrchestratorService.chat("uid-1", ChatRequest.builder()
                .question("Create a budget of 500 for this month")
                .localNowIso("2026-03-15T10:00:00Z")
                .build());

        assertThat(response.getIntent()).isEqualTo("add_budget");
        assertThat(response.getSilentAction()).isTrue();
        assertThat(response.getPayload()).isNotNull();
        assertThat(response.getPayload().getKind()).isEqualTo("budget");
        assertThat(response.getPayload().getMonth()).isEqualTo("2026-03");
        assertThat(response.getPayload().getTotalAmount()).isEqualTo(500.0);
        assertThat(response.getAnswer()).isBlank();
    }

    @Test
    void chat_shouldExtractGoalPayload() {
        parsedResponse = Map.of(
                "intent", "add_goal",
                "confidence", 0.95,
                "payload", Map.of(
                        "kind", "goal",
                        "name", "Vacation Fund",
                        "targetAmount", 3000,
                        "currentAmount", 0,
                        "deadline", "2026-12-31",
                        "color", "#10B981",
                        "icon", "target"
                )
        );

        ChatResponse response = aiOrchestratorService.chat("uid-1", ChatRequest.builder()
                .question("Create a savings goal called Vacation Fund for 3000 by 2026-12-31")
                .localNowIso("2026-03-15T10:00:00Z")
                .build());

        assertThat(response.getIntent()).isEqualTo("add_goal");
        assertThat(response.getSilentAction()).isTrue();
        assertThat(response.getPayload()).isNotNull();
        assertThat(response.getPayload().getKind()).isEqualTo("goal");
        assertThat(response.getPayload().getName()).isEqualTo("Vacation Fund");
        assertThat(response.getPayload().getTargetAmount()).isEqualTo(3000.0);
        assertThat(response.getPayload().getDeadline()).isEqualTo("2026-12-31");
        assertThat(response.getAnswer()).isBlank();
    }

    @Test
    void chat_shouldExtractCategoryPayload() {
        parsedResponse = Map.of(
                "intent", "add_category",
                "confidence", 0.91,
                "payload", Map.of(
                        "kind", "category",
                        "name", "Side Hustle",
                        "categoryType", "income",
                        "color", "#6366F1",
                        "icon", "briefcase"
                )
        );

        ChatResponse response = aiOrchestratorService.chat("uid-1", ChatRequest.builder()
                .question("Create an income category called Side Hustle")
                .localNowIso("2026-03-15T10:00:00Z")
                .build());

        assertThat(response.getIntent()).isEqualTo("add_category");
        assertThat(response.getSilentAction()).isTrue();
        assertThat(response.getPayload()).isNotNull();
        assertThat(response.getPayload().getKind()).isEqualTo("category");
        assertThat(response.getPayload().getName()).isEqualTo("Side Hustle");
        assertThat(response.getPayload().getCategoryType()).isEqualTo("income");
        assertThat(response.getAnswer()).isBlank();
    }

    @Test
    void chat_shouldExtractRecurringExpensePayload() {
        parsedResponse = Map.of(
                "intent", "add_recurring_expense",
                "confidence", 0.94,
                "payload", Map.of(
                        "kind", "recurring_expense",
                        "amount", 12,
                        "currency", "USD",
                        "category", "Subscriptions",
                        "note", "Netflix",
                        "frequency", "monthly",
                        "startDate", "2026-03-15",
                        "notificationEnabled", true,
                        "notificationDaysBefore", 1
                )
        );

        ChatResponse response = aiOrchestratorService.chat("uid-1", ChatRequest.builder()
                .question("Add a recurring expense of 12 dollars for Netflix every month starting today")
                .localNowIso("2026-03-15T10:00:00Z")
                .build());

        assertThat(response.getIntent()).isEqualTo("add_recurring_expense");
        assertThat(response.getSilentAction()).isTrue();
        assertThat(response.getPayload()).isNotNull();
        assertThat(response.getPayload().getKind()).isEqualTo("recurring_expense");
        assertThat(response.getPayload().getAmount()).isEqualTo(12.0);
        assertThat(response.getPayload().getCategory()).isEqualTo("Subscriptions");
        assertThat(response.getPayload().getFrequency()).isEqualTo("monthly");
        assertThat(response.getPayload().getStartDate()).isEqualTo("2026-03-15");
        assertThat(response.getAnswer()).isBlank();
    }

    @Test
    void chat_shouldAskSmartCategoryClarificationForTransaction() {
        parsedResponse = Map.of(
                "intent", "add_transaction",
                "confidence", 0.94,
                "transactions", List.of(Map.of(
                        "kind", "transaction",
                        "type", "expense",
                        "amount", 18,
                        "currency", "USD",
                        "note", "Lunch"
                ))
        );

        ChatResponse response = aiOrchestratorService.chat("uid-1", ChatRequest.builder()
                .question("Add an $18 expense")
                .localNowIso("2026-03-15T10:00:00Z")
                .build());

        assertThat(response.getIntent()).isEqualTo("add_transaction");
        assertThat(response.getAnswer()).isEqualTo("""
                What category should I use for the $18.00 expense?
                1. Food
                2. Shopping
                3. Transport
                4. Other""");
    }

    @Test
    void chat_shouldAskSmartCategoryClarificationForRecurringExpense() {
        parsedResponse = Map.of(
                "intent", "add_recurring_expense",
                "confidence", 0.94,
                "payload", Map.of(
                        "kind", "recurring_expense",
                        "amount", 12,
                        "currency", "USD",
                        "note", "Netflix",
                        "frequency", "monthly",
                        "startDate", "2026-03-15"
                )
        );

        ChatResponse response = aiOrchestratorService.chat("uid-1", ChatRequest.builder()
                .question("Add a recurring $12 Netflix expense")
                .localNowIso("2026-03-15T10:00:00Z")
                .build());

        assertThat(response.getIntent()).isEqualTo("add_recurring_expense");
        assertThat(response.getAnswer()).isEqualTo("""
                What category should I use for the $12.00 recurring expense?
                1. Food
                2. Shopping
                3. Transport
                4. Other""");
    }

    private CategoryEntity category(String name, String type) {
        return CategoryEntity.builder()
                .id(UUID.randomUUID())
                .firebaseUid("uid-1")
                .name(name)
                .icon("tag")
                .color("#6366F1")
                .isDefault(false)
                .categoryType(type)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
