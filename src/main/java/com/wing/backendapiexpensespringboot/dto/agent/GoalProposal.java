package com.wing.backendapiexpensespringboot.dto.agent;

public record GoalProposal(
        String name,
        Double targetAmount,
        Double currentAmount,
        String deadline,
        String color,
        String icon
) implements AgentProposal {
}
