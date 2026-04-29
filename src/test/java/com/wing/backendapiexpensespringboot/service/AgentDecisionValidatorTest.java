package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.agent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDecisionValidatorTest {

    private AgentDecisionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AgentDecisionValidator();
    }

    @Test
    void validate_shouldPassValidReadDecision() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.ANSWER_QUESTION,
                AgentRiskLevel.READ_ONLY,
                AgentDataScope.CURRENT_USER_ONLY, false,
                List.of(), "Here's your summary", "read query",
                new AgentQueryPlan("spending", null, null, null, null), null);

        AgentValidationResult result = validator.validate(decision);

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void validate_shouldRejectSqlInUserFacingMessage() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.ANSWER_QUESTION,
                AgentRiskLevel.READ_ONLY,
                AgentDataScope.CURRENT_USER_ONLY, false,
                List.of(), "SELECT * FROM expenses", "sql attempt",
                null, null);

        AgentValidationResult result = validator.validate(decision);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("SQL"));
    }

    @Test
    void validate_shouldEnforceCurrentUserScope() {
        AgentDecision decision = new AgentDecision(
                AgentActionType.ANSWER_QUESTION,
                AgentRiskLevel.READ_ONLY,
                AgentDataScope.ALL_USERS, false,
                List.of(), "ok", "scope violation",
                null, null);

        AgentValidationResult result = validator.validate(decision);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("dataScope"));
    }

    @Test
    void validate_shouldRejectNullActionType() {
        AgentDecision decision = new AgentDecision(
                null,
                AgentRiskLevel.READ_ONLY,
                AgentDataScope.CURRENT_USER_ONLY, false,
                List.of(), "ok", "no action",
                null, null);

        AgentValidationResult result = validator.validate(decision);

        assertThat(result.valid()).isFalse();
    }
}
