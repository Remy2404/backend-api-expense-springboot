package com.wing.backendapiexpensespringboot.dto.agent;

public record AgentPolicyResult(
        boolean allowed,
        AgentRiskLevel enforcedRiskLevel,
        AgentDataScope enforcedDataScope,
        boolean enforcedRequiresConfirmation,
        String blockReason
) {

    public static AgentPolicyResult allowed(
            AgentRiskLevel riskLevel,
            boolean requiresConfirmation) {
        return new AgentPolicyResult(
                true, riskLevel,
                AgentDataScope.CURRENT_USER_ONLY,
                requiresConfirmation, null
        );
    }

    public static AgentPolicyResult blocked(String reason) {
        return new AgentPolicyResult(
                false,
                AgentRiskLevel.DESTRUCTIVE,
                AgentDataScope.CURRENT_USER_ONLY,
                false,
                reason
        );
    }
}
