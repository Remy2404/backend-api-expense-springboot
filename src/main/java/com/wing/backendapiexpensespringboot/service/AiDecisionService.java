package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import com.wing.backendapiexpensespringboot.dto.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDecisionService {

    private final OpenAIClient openAIClient;
    private final OpenRouterConfig openRouterConfig;
    private final ObjectMapper objectMapper;

    private static final String DECISION_SYSTEM_PROMPT = """
            You are an AI finance assistant decision engine.
            Given a user message and their financial context, classify the user's intent
            and extract structured data in a single response.

            Return ONLY valid JSON matching the schema below. No markdown, no explanation.

            RULES:
            - For greetings, general conversation, or safe questions about yourself: use ANSWER_QUESTION.
            - For questions about spending, budgets, goals, or financial data: use ANSWER_QUESTION with a queryPlan.
            - For requests to add/create/record a transaction: use PREPARE_TRANSACTION with a proposal.
            - For requests to set/create a budget: use PREPARE_BUDGET with a proposal.
            - For requests to create/set a savings goal: use PREPARE_GOAL with a proposal.
            - For requests to create a category: use PREPARE_CATEGORY with a proposal.
            - For requests to create a recurring expense/subscription: use PREPARE_RECURRING_EXPENSE with a proposal.
            - If the user message is ambiguous or you need more info: use CLARIFY.
            - If the request is dangerous, unrelated, or unsupported: use UNSUPPORTED.

            CRITICAL:
            - Never output SQL.
            - Never reference other users' data.
            - If you cannot determine the amount, set it to null — do not guess.
            - For transactions, always return a "transactions" array inside the proposal, even for a single item.
            - Use the provided CURRENT_DATE for "today" references.
            - Use the provided categories list for category matching. If no match, return the user's text as-is.

            JSON SCHEMA:
            {
              "actionType": "ANSWER_QUESTION|PREPARE_TRANSACTION|PREPARE_BUDGET|PREPARE_GOAL|PREPARE_CATEGORY|PREPARE_RECURRING_EXPENSE|CLARIFY|UNSUPPORTED",
              "riskLevel": "READ_ONLY|WRITE_FINANCIAL_DATA",
              "dataScope": "CURRENT_USER_ONLY",
              "requiresConfirmation": true|false,
              "missingFields": [],
              "userFacingMessage": "",
              "reasoning": "",
              "queryPlan": { "queryType": "", "dateFrom": "", "dateTo": "", "categoryFilter": "", "groupBy": "" } | null,
              "proposal": <typed proposal object> | null
            }

            PROPOSAL SHAPES:
            PREPARE_TRANSACTION: { "transactions": [{ "type": "income|expense", "amount": null|number, "currency": "USD", "category": "", "note": "", "noteSummary": "", "date": "YYYY-MM-DD", "merchant": "" }] }
            PREPARE_BUDGET: { "month": "YYYY-MM", "totalAmount": number }
            PREPARE_GOAL: { "name": "", "targetAmount": number, "currentAmount": 0, "deadline": "YYYY-MM-DD", "color": "#10B981", "icon": "target" }
            PREPARE_CATEGORY: { "name": "", "categoryType": "expense|income", "color": "#6366F1", "icon": "tag" }
            PREPARE_RECURRING_EXPENSE: { "type": "expense|income", "amount": number, "currency": "USD", "category": "", "note": "", "frequency": "daily|weekly|biweekly|monthly|yearly", "startDate": "YYYY-MM-DD", "endDate": null, "notificationEnabled": true, "notificationDaysBefore": 1 }
            """;

    public AgentDecision classify(String question, List<String> categoryNames,
            String localToday, String currentMonth) {
        String contextBlock = buildContextBlock(categoryNames, localToday, currentMonth);
        String fullSystemPrompt = DECISION_SYSTEM_PROMPT + "\n" + contextBlock;

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String rawJson = callLlm(fullSystemPrompt, question);
                return parseDecision(rawJson);
            } catch (Exception e) {
                log.warn("Decision parse attempt {} failed: {}", attempt + 1, e.getMessage());
                if (attempt == 1) {
                    log.error("Both decision parse attempts failed for question: {}",
                            question.length() > 100 ? question.substring(0, 100) : question);
                }
            }
        }

        return AgentDecision.clarify("I couldn't understand your request. Could you rephrase?");
    }

    private String callLlm(String systemPrompt, String userMessage) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(openRouterConfig.getModel())
                .temperature(0.2)
                .addMessage(ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam.builder()
                                .content(systemPrompt)
                                .build()))
                .addMessage(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(userMessage)
                                .build()))
                .responseFormat(ChatCompletionCreateParams.ResponseFormat.ofJsonObject(
                        ResponseFormatJsonObject.builder().build()))
                .build();

        ChatCompletion completion = openAIClient.chat().completions().create(params);

        List<ChatCompletion.Choice> choices = completion.choices();
        if (choices.isEmpty()) {
            throw new IllegalStateException("No choices returned from LLM");
        }

        String content = choices.get(0).message().content().orElse("");
        if (content.isBlank()) {
            throw new IllegalStateException("Empty content from LLM");
        }

        return content;
    }

    @SuppressWarnings("unchecked")
    private AgentDecision parseDecision(String rawJson) throws Exception {
        String cleaned = rawJson.replaceAll("```json", "").replaceAll("```", "").trim();
        Map<String, Object> map = objectMapper.readValue(cleaned, Map.class);

        AgentActionType actionType = parseActionType((String) map.get("actionType"));
        AgentRiskLevel riskLevel = parseRiskLevel((String) map.get("riskLevel"));
        AgentDataScope dataScope = AgentDataScope.CURRENT_USER_ONLY;
        boolean requiresConfirmation = Boolean.TRUE.equals(map.get("requiresConfirmation"));

        List<String> missingFields = map.get("missingFields") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : List.of();

        String userFacingMessage = stringVal(map.get("userFacingMessage"));
        String reasoning = stringVal(map.get("reasoning"));

        AgentQueryPlan queryPlan = parseQueryPlan(map.get("queryPlan"));
        AgentProposal proposal = parseProposal(actionType, map.get("proposal"));

        return new AgentDecision(
                actionType, riskLevel, dataScope, requiresConfirmation,
                missingFields, userFacingMessage, reasoning, queryPlan, proposal);
    }

    private AgentActionType parseActionType(String value) {
        if (value == null)
            return AgentActionType.CLARIFY;
        try {
            return AgentActionType.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown action type: {}", value);
            return AgentActionType.CLARIFY;
        }
    }

    private AgentRiskLevel parseRiskLevel(String value) {
        if (value == null)
            return AgentRiskLevel.UNKNOWN;
        try {
            return AgentRiskLevel.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return AgentRiskLevel.UNKNOWN;
        }
    }

    private AgentQueryPlan parseQueryPlan(Object raw) {
        if (!(raw instanceof Map<?, ?> map))
            return null;
        return new AgentQueryPlan(
                stringVal(map.get("queryType")),
                stringVal(map.get("dateFrom")),
                stringVal(map.get("dateTo")),
                stringVal(map.get("categoryFilter")),
                stringVal(map.get("groupBy")));
    }

    private AgentProposal parseProposal(AgentActionType actionType, Object raw) {
        if (raw == null || !(raw instanceof Map<?, ?>))
            return null;

        try {
            String json = objectMapper.writeValueAsString(raw);
            return switch (actionType) {
                case PREPARE_TRANSACTION -> objectMapper.readValue(json, TransactionProposal.class);
                case PREPARE_BUDGET -> objectMapper.readValue(json, BudgetProposal.class);
                case PREPARE_GOAL -> objectMapper.readValue(json, GoalProposal.class);
                case PREPARE_CATEGORY -> objectMapper.readValue(json, CategoryProposal.class);
                case PREPARE_RECURRING_EXPENSE -> objectMapper.readValue(json, RecurringExpenseProposal.class);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("Failed to parse proposal for {}: {}", actionType, e.getMessage());
            return null;
        }
    }

    private String buildContextBlock(List<String> categoryNames, String localToday, String currentMonth) {
        StringBuilder sb = new StringBuilder();
        sb.append("CURRENT_DATE=").append(localToday).append("\n");
        sb.append("CURRENT_MONTH=").append(currentMonth).append("\n");
        sb.append("AVAILABLE_CATEGORIES=").append(String.join(", ", categoryNames)).append("\n");
        return sb.toString();
    }

    private String stringVal(Object raw) {
        if (raw == null)
            return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }
}
