package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AgentDecisionValidator {

    private static final Pattern SQL_PATTERN = Pattern.compile(
            "(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|EXEC|UNION|--|;)\\s",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(previous|above)|system\\s*prompt|you\\s+are\\s+now|act\\s+as|pretend|override|bypass|forget\\s+(all|everything)|disregard)",
            Pattern.CASE_INSENSITIVE
    );

    public AgentValidationResult validate(AgentDecision decision) {
        List<String> violations = new ArrayList<>();

        if (decision.actionType() == null) {
            violations.add("Missing actionType");
        }

        if (decision.dataScope() != AgentDataScope.CURRENT_USER_ONLY) {
            violations.add("Invalid dataScope: must be CURRENT_USER_ONLY");
        }

        if (decision.userFacingMessage() != null) {
            scanForInjection(decision.userFacingMessage(), violations, "userFacingMessage");
        }

        if (decision.reasoning() != null) {
            scanForInjection(decision.reasoning(), violations, "reasoning");
        }

        if (isWriteAction(decision.actionType()) && decision.proposal() == null) {
            violations.add("Write action requires a proposal");
        }

        if (decision.queryPlan() != null && decision.queryPlan().queryType() != null) {
            scanForInjection(decision.queryPlan().queryType(), violations, "queryPlan.queryType");
        }

        validateProposal(decision.actionType(), decision.proposal(), violations);

        if (!violations.isEmpty()) {
            log.warn("Agent decision validation failed: {}", violations);
            return AgentValidationResult.invalid(violations);
        }

        AgentDecision sanitized = new AgentDecision(
                decision.actionType(),
                decision.riskLevel(),
                AgentDataScope.CURRENT_USER_ONLY,
                decision.requiresConfirmation(),
                decision.missingFields() != null ? decision.missingFields() : List.of(),
                decision.userFacingMessage(),
                decision.reasoning(),
                decision.queryPlan(),
                decision.proposal()
        );

        return AgentValidationResult.valid(sanitized);
    }

    private void scanForInjection(String text, List<String> violations, String field) {
        if (SQL_PATTERN.matcher(text).find()) {
            violations.add("SQL injection detected in " + field);
        }
        if (PROMPT_INJECTION_PATTERN.matcher(text).find()) {
            violations.add("Prompt injection detected in " + field);
        }
    }

    private boolean isWriteAction(AgentActionType actionType) {
        return actionType != null && actionType.name().startsWith("PREPARE_");
    }

    private void validateProposal(AgentActionType actionType, AgentProposal proposal,
                                  List<String> violations) {
        if (proposal == null) return;

        switch (proposal) {
            case TransactionProposal tp -> {
                if (tp.transactions() == null || tp.transactions().isEmpty()) {
                    violations.add("TransactionProposal requires at least one transaction");
                } else {
                    for (int i = 0; i < tp.transactions().size(); i++) {
                        TransactionProposal.TransactionItem item = tp.transactions().get(i);
                        if (item.type() == null || item.type().isBlank()) {
                            violations.add("Transaction[" + i + "] missing type");
                        }
                        if (item.note() != null) {
                            scanForInjection(item.note(), violations, "transaction[" + i + "].note");
                        }
                    }
                }
            }
            case BudgetProposal bp -> {
                if (bp.month() == null || bp.month().isBlank()) {
                    violations.add("BudgetProposal requires month");
                }
                if (bp.totalAmount() == null || bp.totalAmount() <= 0) {
                    violations.add("BudgetProposal requires positive totalAmount");
                }
            }
            case GoalProposal gp -> {
                if (gp.name() == null || gp.name().isBlank()) {
                    violations.add("GoalProposal requires name");
                }
            }
            case CategoryProposal cp -> {
                if (cp.name() == null || cp.name().isBlank()) {
                    violations.add("CategoryProposal requires name");
                }
            }
            case RecurringExpenseProposal rp -> {
                if (rp.frequency() == null || rp.frequency().isBlank()) {
                    violations.add("RecurringExpenseProposal requires frequency");
                }
            }
        }
    }
}
