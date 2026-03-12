package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AiOrchestratorServiceIntentDetectionTest {

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

    @Test
    void detectIntent_shouldClassifyBareIncomeStatementAsAddExpense() {
        String intent = ReflectionTestUtils.invokeMethod(
                aiOrchestratorService,
                "detectIntent",
                "$2.5 entertainment income"
        );

        assertThat(intent).isEqualTo("add_expense");
    }

    @Test
    void detectIntent_shouldClassifyAmountOnlyStatementAsAddExpense() {
        String intent = ReflectionTestUtils.invokeMethod(
                aiOrchestratorService,
                "detectIntent",
                "2.40 coffee"
        );

        assertThat(intent).isEqualTo("add_expense");
    }

    @Test
    void detectIntent_shouldClassifyQuestionAsQuery() {
        String intent = ReflectionTestUtils.invokeMethod(
                aiOrchestratorService,
                "detectIntent",
                "How much did I spend this week?"
        );

        assertThat(intent).isEqualTo("query_expenses");
    }

    @Test
    void detectIntent_shouldClassifyExplicitAddActionAsAddExpense() {
        String intent = ReflectionTestUtils.invokeMethod(
                aiOrchestratorService,
                "detectIntent",
                "Please add 5.50 for coffee"
        );

        assertThat(intent).isEqualTo("add_expense");
    }

    @Test
    void detectIntent_shouldClassifyGenericTextAsNone() {
        String intent = ReflectionTestUtils.invokeMethod(
                aiOrchestratorService,
                "detectIntent",
                "Good afternoon"
        );

        assertThat(intent).isEqualTo("none");
    }
}
