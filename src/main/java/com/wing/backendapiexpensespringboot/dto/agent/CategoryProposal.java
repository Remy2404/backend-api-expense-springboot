package com.wing.backendapiexpensespringboot.dto.agent;

public record CategoryProposal(
        String name,
        String categoryType,
        String color,
        String icon
) implements AgentProposal {
}
