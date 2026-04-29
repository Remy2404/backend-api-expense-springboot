package com.wing.backendapiexpensespringboot.dto.agent;

public record AgentQueryPlan(
        String queryType,
        String dateFrom,
        String dateTo,
        String categoryFilter,
        String groupBy
) {}
