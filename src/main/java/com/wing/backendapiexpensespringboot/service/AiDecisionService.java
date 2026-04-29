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
import com.wing.backendapiexpensespringboot.dto.ChatHistoryMessage;
import com.wing.backendapiexpensespringboot.dto.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        return classify(question, List.of(), categoryNames, localToday, currentMonth);
    }

    public AgentDecision classify(String question, List<ChatHistoryMessage> history, List<String> categoryNames,
            String localToday, String currentMonth) {
        String contextBlock = buildContextBlock(categoryNames, localToday, currentMonth);
        String fullSystemPrompt = DECISION_SYSTEM_PROMPT + "\n" + contextBlock;
        String decisionInput = buildDecisionInput(history, question);
        List<String> modelCandidates = buildModelCandidates();

        for (String model : modelCandidates) {
            for (int attempt = 0; attempt < 2; attempt++) {
                boolean strictJsonMode = attempt == 0;
                try {
                    String rawJson = callLlm(fullSystemPrompt, decisionInput, model, strictJsonMode);
                    return parseDecision(rawJson, currentMonth);
                } catch (Exception e) {
                    log.warn("Decision attempt {} failed for model {} (strictJsonMode={}): {}",
                            attempt + 1, model, strictJsonMode, e.getMessage());
                }
            }
        }

        log.error("All decision attempts failed for question: {}",
                question.length() > 100 ? question.substring(0, 100) : question);
        return AgentDecision.clarify("I couldn't understand your request. Could you rephrase?");
    }

    private String callLlm(String systemPrompt, String userMessage, String model, boolean strictJsonMode) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(model)
                .temperature(0.2)
                .addMessage(ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam.builder()
                                .content(systemPrompt)
                                .build()))
                .addMessage(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(userMessage)
                                .build()));

        if (strictJsonMode) {
            builder.responseFormat(ChatCompletionCreateParams.ResponseFormat.ofJsonObject(
                    ResponseFormatJsonObject.builder().build()));
        }

        ChatCompletionCreateParams params = builder.build();

        ChatCompletion completion = openAIClient.chat().completions().create(params);

        List<ChatCompletion.Choice> choices = completion.choices();
        if (choices.isEmpty()) {
            throw new IllegalStateException("No choices returned from LLM");
        }

        String content = choices.get(0).message().content().orElse("");
        if (content.isBlank()) {
            throw new IllegalStateException("Empty content from LLM");
        }

        return content.trim();
    }

    @SuppressWarnings("unchecked")
    AgentDecision parseDecision(String rawJson, String currentMonth) throws Exception {
        String cleaned = sanitizeJsonPayload(rawJson);
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
        AgentProposal proposal = parseProposal(actionType, map.get("proposal"), currentMonth);

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

    private AgentProposal parseProposal(AgentActionType actionType, Object raw, String currentMonth) {
        if (raw == null || !(raw instanceof Map<?, ?>))
            return null;

        try {
            String json = objectMapper.writeValueAsString(raw);
            return switch (actionType) {
                case PREPARE_TRANSACTION -> parseTransactionProposal((Map<?, ?>) raw);
                case PREPARE_BUDGET -> parseBudgetProposal((Map<?, ?>) raw, currentMonth);
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

    private TransactionProposal parseTransactionProposal(Map<?, ?> rawMap) {
        List<TransactionProposal.TransactionItem> items = new ArrayList<>();

        Object transactionsRaw = rawMap.get("transactions");
        if (transactionsRaw instanceof List<?> txList) {
            for (Object item : txList) {
                TransactionProposal.TransactionItem parsed = parseTransactionItem(item);
                if (parsed != null) {
                    items.add(parsed);
                }
            }
        } else if (transactionsRaw instanceof Map<?, ?> txMap) {
            TransactionProposal.TransactionItem parsed = parseTransactionItem(txMap);
            if (parsed != null) {
                items.add(parsed);
            }
        } else {
            TransactionProposal.TransactionItem parsed = parseTransactionItem(rawMap);
            if (parsed != null) {
                items.add(parsed);
            }
        }

        return new TransactionProposal(items);
    }

    private TransactionProposal.TransactionItem parseTransactionItem(Object rawItem) {
        if (!(rawItem instanceof Map<?, ?> itemMap)) {
            return null;
        }

        String type = stringVal(firstNonNull(
                itemMap.get("type"),
                itemMap.get("transactionType")));
        Double amount = parseNumber(firstNonNull(
                itemMap.get("amount"),
                itemMap.get("totalAmount"),
                itemMap.get("value")));
        String currency = stringVal(firstNonNull(
                itemMap.get("currency"),
                itemMap.get("currencyCode")));
        String category = stringVal(firstNonNull(
                itemMap.get("category"),
                itemMap.get("categoryName")));
        String note = stringVal(itemMap.get("note"));
        String noteSummary = stringVal(firstNonNull(
                itemMap.get("noteSummary"),
                itemMap.get("note_summary")));
        String date = stringVal(itemMap.get("date"));
        String merchant = stringVal(firstNonNull(
                itemMap.get("merchant"),
                itemMap.get("merchantName")));

        return new TransactionProposal.TransactionItem(
                type, amount, currency, category, note, noteSummary, date, merchant);
    }

    private BudgetProposal parseBudgetProposal(Map<?, ?> rawMap, String currentMonth) {
        String month = stringVal(firstNonNull(rawMap.get("month"), rawMap.get("period")));
        if (month == null) {
            month = currentMonth;
        }

        Double totalAmount = parseNumber(firstNonNull(
                rawMap.get("totalAmount"),
                rawMap.get("total"),
                rawMap.get("amount"),
                rawMap.get("budgetAmount"),
                rawMap.get("limit")));
        return new BudgetProposal(month, totalAmount);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Double parseNumber(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        String normalized = text.replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || normalized.equals("-") || normalized.equals(".")) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
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

    private String buildDecisionInput(List<ChatHistoryMessage> history, String question) {
        if (history == null || history.isEmpty()) {
            return question;
        }

        String conversation = history.stream()
                .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                .skip(Math.max(0, history.size() - 6))
                .map(m -> {
                    String role = m.getRole() == null ? "user" : m.getRole().trim().toLowerCase();
                    return role + ": " + m.getContent().trim();
                })
                .collect(Collectors.joining("\n"));

        return "Conversation history:\n" + conversation + "\nlatest_user: " + question;
    }

    List<String> buildModelCandidates() {
        List<String> candidates = new ArrayList<>();
        candidates.add(openRouterConfig.getModel());

        String fallbackModelsRaw = openRouterConfig.getFallbackModels();
        if (fallbackModelsRaw != null && !fallbackModelsRaw.isBlank()) {
            Arrays.stream(fallbackModelsRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(candidates::add);
        }

        return candidates.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    String sanitizeJsonPayload(String rawJson) {
        if (rawJson == null) {
            throw new IllegalStateException("LLM response is null");
        }

        String cleaned = rawJson.replaceAll("```json", "").replaceAll("```", "").trim();
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            return cleaned;
        }

        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1);
        }

        throw new IllegalStateException("No JSON object found in model response");
    }

    private String stringVal(Object raw) {
        if (raw == null)
            return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }
}
