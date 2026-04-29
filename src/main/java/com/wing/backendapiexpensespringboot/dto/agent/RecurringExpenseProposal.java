package com.wing.backendapiexpensespringboot.dto.agent;

public record RecurringExpenseProposal(
        String type,
        Double amount,
        String currency,
        String category,
        String note,
        String frequency,
        String startDate,
        String endDate,
        Boolean notificationEnabled,
        Integer notificationDaysBefore
) implements AgentProposal {
}
