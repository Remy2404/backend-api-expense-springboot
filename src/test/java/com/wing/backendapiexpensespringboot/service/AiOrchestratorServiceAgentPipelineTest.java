package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ChatRequest;
import com.wing.backendapiexpensespringboot.dto.ChatResponse;
import com.wing.backendapiexpensespringboot.dto.agent.*;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiOrchestratorServiceAgentPipelineTest {

    @Mock private OpenRouterService openRouterService;
    @Mock private ExpenseService expenseService;
    @Mock private CategoryService categoryService;
    @Mock private MemoryService memoryService;
    @Mock private CorrectionService correctionService;
    @Mock private SafetyValidatorService safetyValidatorService;
    @Mock private com.wing.backendapiexpensespringboot.config.OpenRouterConfig openRouterConfig;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock private AiDecisionService aiDecisionService;
    @Mock private AgentDecisionValidator agentDecisionValidator;
    @Mock private AgentPolicyService agentPolicyService;
    @Mock private PendingAiActionService pendingAiActionService;

    @InjectMocks
    private AiOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        List<CategoryEntity> categories = List.of(
                category("Food"), category("Shopping"),
                category("Transport"), category("Other"));
        lenient().when(categoryService.getCategories(anyString())).thenReturn(categories);
    }

    @Test
    void chat_shouldReturnClarifyWhenValidationFails() {
        AgentDecision raw = AgentDecision.clarify("What do you mean?");
        when(aiDecisionService.classify(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(raw);
        when(agentDecisionValidator.validate(any()))
                .thenReturn(new AgentValidationResult(false, List.of("SQL injection detected"), null));

        ChatResponse response = orchestrator.chat("uid-1", chatRequest("DROP TABLE users"));

        assertThat(response.getActionType()).isEqualTo("CLARIFY");
        assertThat(response.getSafetyWarnings()).contains("SQL injection detected");
        assertThat(response.getNeedsConfirmation()).isFalse();
    }

    @Test
    void chat_shouldReturnUnsupportedWhenPolicyBlocks() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.PREPARE_TRANSACTION,
                AgentRiskLevel.WRITE_FINANCIAL_DATA,
                AgentDataScope.CURRENT_USER_ONLY, true,
                List.of(), "Adding expense", "user wants to add",
                null, new TransactionProposal(List.of()));

        when(aiDecisionService.classify(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(decision);
        when(agentDecisionValidator.validate(any()))
                .thenReturn(new AgentValidationResult(true, List.of(), decision));
        when(agentPolicyService.evaluate(any()))
                .thenReturn(AgentPolicyResult.blocked("Action blocked by policy"));

        ChatResponse response = orchestrator.chat("uid-1", chatRequest("Add $50 food"));

        assertThat(response.getActionType()).isEqualTo("UNSUPPORTED");
        assertThat(response.getSafetyWarnings()).contains("Action blocked by policy");
    }

    @Test
    void chat_shouldReturnReadResponseForAnswerQuestion() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.ANSWER_QUESTION,
                AgentRiskLevel.READ_ONLY,
                AgentDataScope.CURRENT_USER_ONLY, false,
                List.of(), null, "user asks spending",
                new AgentQueryPlan("spending_summary", null, null, null, null),
                null);

        when(aiDecisionService.classify(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(decision);
        when(agentDecisionValidator.validate(any()))
                .thenReturn(new AgentValidationResult(true, List.of(), decision));
        when(agentPolicyService.evaluate(any()))
                .thenReturn(AgentPolicyResult.allowed(AgentRiskLevel.READ_ONLY, false));
        when(expenseService.getExpensesBetween(anyString(), any(), any())).thenReturn(List.of());
        when(openRouterService.chat(anyString(), anyString())).thenReturn("You spent $0 this month.");

        ChatResponse response = orchestrator.chat("uid-1", chatRequest("How much did I spend?"));

        assertThat(response.getActionType()).isEqualTo("ANSWER_QUESTION");
        assertThat(response.getAnswer()).isEqualTo("You spent $0 this month.");
        assertThat(response.getNeedsConfirmation()).isFalse();
    }

    @Test
    void chat_shouldReturnPendingActionForWriteProposal() {
        BudgetProposal proposal = new BudgetProposal("2026-03", 500.0);
        AgentDecision decision = new AgentDecision(
                AgentActionType.PREPARE_BUDGET,
                AgentRiskLevel.WRITE_FINANCIAL_DATA,
                AgentDataScope.CURRENT_USER_ONLY, true,
                List.of(), "I'll create a $500 budget for March.", "user wants budget",
                null, proposal);

        UUID pendingId = UUID.randomUUID();

        when(aiDecisionService.classify(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(decision);
        when(agentDecisionValidator.validate(any()))
                .thenReturn(new AgentValidationResult(true, List.of(), decision));
        when(agentPolicyService.evaluate(any()))
                .thenReturn(AgentPolicyResult.allowed(AgentRiskLevel.WRITE_FINANCIAL_DATA, true));
        when(pendingAiActionService.store(anyString(), any(), any())).thenReturn(pendingId);

        ChatResponse response = orchestrator.chat("uid-1", chatRequest("Create a budget of 500"));

        assertThat(response.getActionType()).isEqualTo("PREPARE_BUDGET");
        assertThat(response.getPendingActionId()).isEqualTo(pendingId.toString());
        assertThat(response.getNeedsConfirmation()).isTrue();
        assertThat(response.getSilentAction()).isFalse();
    }

    @Test
    void chat_shouldReturnClarifyWhenProposalIsNull() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.PREPARE_TRANSACTION,
                AgentRiskLevel.WRITE_FINANCIAL_DATA,
                AgentDataScope.CURRENT_USER_ONLY, true,
                List.of("amount"), "How much was it?", "missing amount",
                null, null);

        when(aiDecisionService.classify(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(decision);
        when(agentDecisionValidator.validate(any()))
                .thenReturn(new AgentValidationResult(true, List.of(), decision));
        when(agentPolicyService.evaluate(any()))
                .thenReturn(AgentPolicyResult.allowed(AgentRiskLevel.WRITE_FINANCIAL_DATA, true));

        ChatResponse response = orchestrator.chat("uid-1", chatRequest("Add expense"));

        assertThat(response.getActionType()).isEqualTo("CLARIFY");
        assertThat(response.getMissingFields()).contains("amount");
        assertThat(response.getNeedsConfirmation()).isFalse();
    }

    @Test
    void chat_shouldEnforceSafetyValidation() {
        AgentDecision decision = AgentDecision.clarify("Hello!");
        when(aiDecisionService.classify(anyString(), anyList(), anyString(), anyString()))
                .thenReturn(decision);
        when(agentDecisionValidator.validate(any()))
                .thenReturn(new AgentValidationResult(false, List.of("blocked"), null));

        orchestrator.chat("uid-1", chatRequest("Hello"));
        verify(safetyValidatorService).enforceNoAutoDelete("Hello");
    }

    private ChatRequest chatRequest(String question) {
        return ChatRequest.builder()
                .question(question)
                .localNowIso("2026-03-15T10:00:00Z")
                .build();
    }

    private CategoryEntity category(String name) {
        return CategoryEntity.builder()
                .id(UUID.randomUUID())
                .firebaseUid("uid-1")
                .name(name)
                .icon("tag")
                .color("#6366F1")
                .isDefault(false)
                .categoryType("expense")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
