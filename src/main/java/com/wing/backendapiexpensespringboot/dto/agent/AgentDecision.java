package com.wing.backendapiexpensespringboot.dto.agent;

import java.util.List;

public record AgentDecision(
        AgentActionType actionType,
        AgentRiskLevel riskLevel,
        AgentDataScope dataScope,
        boolean requiresConfirmation,
        List<String> missingFields,
        String userFacingMessage,
        String reasoning,
        AgentQueryPlan queryPlan,
        AgentProposal proposal
) {

    public static AgentDecision clarify(String message) {
        return new AgentDecision(
                AgentActionType.CLARIFY,
                AgentRiskLevel.UNKNOWN,
                AgentDataScope.CURRENT_USER_ONLY,
                false,
                List.of(),
                message,
                "Could not determine action",
                null,
                null
        );
    }

    public static AgentDecision unsupported(String message) {
        return new AgentDecision(
                AgentActionType.UNSUPPORTED,
                AgentRiskLevel.UNKNOWN,
                AgentDataScope.CURRENT_USER_ONLY,
                false,
                List.of(),
                message,
                "Action not supported",
                null,
                null
        );
    }
}
