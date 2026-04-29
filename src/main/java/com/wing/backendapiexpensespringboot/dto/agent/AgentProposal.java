package com.wing.backendapiexpensespringboot.dto.agent;

public sealed interface AgentProposal
        permits TransactionProposal, BudgetProposal, GoalProposal,
                CategoryProposal, RecurringExpenseProposal {
}
