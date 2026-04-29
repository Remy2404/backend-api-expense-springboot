package com.wing.backendapiexpensespringboot.dto.agent;

import java.util.List;

public record TransactionProposal(
        List<TransactionItem> transactions
) implements AgentProposal {

    public record TransactionItem(
            String type,
            Double amount,
            String currency,
            String category,
            String note,
            String noteSummary,
            String date,
            String merchant
    ) {}
}
