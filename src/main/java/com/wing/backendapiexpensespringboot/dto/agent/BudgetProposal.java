package com.wing.backendapiexpensespringboot.dto.agent;

public record BudgetProposal(
        String month,
        Double totalAmount
) implements AgentProposal {
}
