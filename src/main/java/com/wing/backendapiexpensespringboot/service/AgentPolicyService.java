package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class AgentPolicyService {

    private static final Map<AgentActionType, AgentPolicyRule> POLICY_TABLE = Map.of(
            AgentActionType.ANSWER_QUESTION, new AgentPolicyRule(
                    AgentRiskLevel.READ_ONLY, false, true),
            AgentActionType.PREPARE_TRANSACTION, new AgentPolicyRule(
                    AgentRiskLevel.WRITE_FINANCIAL_DATA, true, true),
            AgentActionType.PREPARE_BUDGET, new AgentPolicyRule(
                    AgentRiskLevel.WRITE_FINANCIAL_DATA, true, true),
            AgentActionType.PREPARE_GOAL, new AgentPolicyRule(
                    AgentRiskLevel.WRITE_FINANCIAL_DATA, true, true),
            AgentActionType.PREPARE_CATEGORY, new AgentPolicyRule(
                    AgentRiskLevel.WRITE_FINANCIAL_DATA, true, true),
            AgentActionType.PREPARE_RECURRING_EXPENSE, new AgentPolicyRule(
                    AgentRiskLevel.WRITE_FINANCIAL_DATA, true, true),
            AgentActionType.CLARIFY, new AgentPolicyRule(
                    AgentRiskLevel.READ_ONLY, false, true),
            AgentActionType.UNSUPPORTED, new AgentPolicyRule(
                    AgentRiskLevel.READ_ONLY, false, true)
    );

    public AgentPolicyResult evaluate(AgentDecision decision) {
        AgentPolicyRule rule = POLICY_TABLE.get(decision.actionType());

        if (rule == null) {
            log.warn("No policy rule for action type: {}", decision.actionType());
            return AgentPolicyResult.blocked("Action not allowed by policy");
        }

        if (!rule.allowed()) {
            return AgentPolicyResult.blocked("Action blocked by policy: " + decision.actionType());
        }

        return AgentPolicyResult.allowed(rule.enforcedRiskLevel(), rule.requiresConfirmation());
    }

    private record AgentPolicyRule(
            AgentRiskLevel enforcedRiskLevel,
            boolean requiresConfirmation,
            boolean allowed
    ) {}
}
