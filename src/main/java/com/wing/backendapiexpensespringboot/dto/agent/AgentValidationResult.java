package com.wing.backendapiexpensespringboot.dto.agent;

import java.util.List;

public record AgentValidationResult(
        boolean valid,
        List<String> violations,
        AgentDecision sanitizedDecision
) {

    public static AgentValidationResult valid(AgentDecision decision) {
        return new AgentValidationResult(true, List.of(), decision);
    }

    public static AgentValidationResult invalid(List<String> violations) {
        return new AgentValidationResult(
                false,
                violations,
                AgentDecision.clarify("I need more information to help you with that.")
        );
    }
}
