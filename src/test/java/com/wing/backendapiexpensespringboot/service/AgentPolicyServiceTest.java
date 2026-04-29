package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.agent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPolicyServiceTest {

    private AgentPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new AgentPolicyService();
    }

    @Test
    void evaluate_shouldAllowReadActions() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.ANSWER_QUESTION,
                AgentRiskLevel.READ_ONLY,
                AgentDataScope.CURRENT_USER_ONLY, false,
                List.of(), null, null, null, null);

        AgentPolicyResult result = policyService.evaluate(decision);

        assertThat(result.allowed()).isTrue();
        assertThat(result.enforcedRequiresConfirmation()).isFalse();
    }

    @Test
    void evaluate_shouldForceConfirmationForWriteActions() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.PREPARE_BUDGET,
                AgentRiskLevel.WRITE_FINANCIAL_DATA,
                AgentDataScope.CURRENT_USER_ONLY, false,
                List.of(), null, null, null,
                new BudgetProposal("2026-03", 500.0));

        AgentPolicyResult result = policyService.evaluate(decision);

        assertThat(result.allowed()).isTrue();
        assertThat(result.enforcedRequiresConfirmation()).isTrue();
    }

    @Test
    void evaluate_shouldAllowClarifyActions() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.CLARIFY,
                AgentRiskLevel.READ_ONLY,
                AgentDataScope.CURRENT_USER_ONLY, false,
                List.of(), "Need more info", null, null, null);

        AgentPolicyResult result = policyService.evaluate(decision);

        assertThat(result.allowed()).isTrue();
        assertThat(result.enforcedRequiresConfirmation()).isFalse();
    }

    @Test
    void evaluate_shouldAllowUnsupportedActions() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.UNSUPPORTED,
                AgentRiskLevel.READ_ONLY,
                AgentDataScope.CURRENT_USER_ONLY, false,
                List.of(), "I can't do that", null, null, null);

        AgentPolicyResult result = policyService.evaluate(decision);

        assertThat(result.allowed()).isTrue();
    }
}
