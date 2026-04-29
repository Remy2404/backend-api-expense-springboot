package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import com.wing.backendapiexpensespringboot.dto.*;
import com.wing.backendapiexpensespringboot.dto.agent.*;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.CategoryType;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.model.MemoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestratorService {

    private final OpenRouterService openRouterService;
    private final ExpenseService expenseService;
    private final CategoryService categoryService;
    private final MemoryService memoryService;
    private final CorrectionService correctionService;
    private final SafetyValidatorService safetyValidatorService;
    private final OpenRouterConfig openRouterConfig;
    private final ObjectMapper objectMapper;
    private final AiDecisionService aiDecisionService;
    private final AgentDecisionValidator agentDecisionValidator;
    private final AgentPolicyService agentPolicyService;
    private final PendingAiActionService pendingAiActionService;

    private static final String CATEGORIZE_PROMPT = """
            You are an expense categorizer. Given a merchant name and optional note,
            select the most appropriate category from the provided list.
            Return JSON with: categoryId (string), confidence (0-1), reason (string).
            """;

    private static final String CHAT_PROMPT_TEMPLATE = """
            You are a Smart Personal Finance Assistant grounded in given data only.
            Answer the user's question using ONLY the provided expense dataset.
            Never mention internal limitations, datasets, or tooling.
            When analyzing spending, include concise category breakdown if available.
            CURRENT_DATE=%s
            User timezone: %s.
            """;

    public ParseResponse parse(String firebaseUid, ParseRequest request) {
        log.info("Parsing expense for user: {}", firebaseUid);
        String preferredCurrency = "USD";
        String source = "openrouter";
        String merchant = null;
        String suggestedCategoryId = null;

        List<CategoryEntity> categories = categoryService.getCategories(firebaseUid);
        String categoryList = categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.joining(", "));

        String parsePromptWithCategories = String.format(
                "You are an expense parser. Parse the following text and extract expense information.\n" +
                        "Return JSON with: amount (number), currency (string), merchant (string), date (YYYY-MM-DD), note (string), noteSummary (string), category (string).\n"
                        +
                        "If you cannot determine a value, use null.\n" +
                        "- note: A professional title in Title Case (1-3 words). E.g. 'Coffee', 'Groceries', 'Gasoline'.\n"
                        +
                        "- noteSummary: A brief summary (5-15 words) with relevant details like who, what, where. E.g. 'Morning coffee at Starbucks with colleague', 'Weekly groceries shopping at Walmart'.\n"
                        +
                        "Available categories: %s. If the expense doesn't match any category, create a new category name (e.g., 'Drink', 'Snack').",
                categoryList);

        try {
            String response = openRouterService.chat(parsePromptWithCategories, request.getRawText());
            Map<String, Object> parsed = parseJsonResponse(response);

            merchant = (String) parsed.get("merchant");
            String parsedCategory = (String) parsed.get("category");
            String noteSummary = (String) parsed.get("noteSummary");

            if (merchant != null && !merchant.isEmpty()) {
                Optional<MemoryEntity> memory = memoryService.getByMerchant(firebaseUid, merchant);
                if (memory.isPresent() && memory.get().getResolvedCategoryId() != null) {
                    suggestedCategoryId = memory.get().getResolvedCategoryId().toString();
                    source = "memory";
                }
            }

            if (suggestedCategoryId == null && parsedCategory != null && !parsedCategory.isEmpty()) {
                CategoryEntity categoryEntity = resolveCategory(parsedCategory, categories);
                if (categoryEntity != null) {
                    suggestedCategoryId = categoryEntity.getId().toString();
                }
            }

            Double confidence = parsed.containsKey("confidence") ? ((Number) parsed.get("confidence")).doubleValue()
                    : 0.8;
            if (suggestedCategoryId != null) {
                confidence = Math.max(confidence, 0.95);
            }

            LocalDate parsedDate = null;
            if (parsed.containsKey("date") && parsed.get("date") != null) {
                try {
                    parsedDate = LocalDate.parse((String) parsed.get("date"));
                } catch (Exception e) {
                    parsedDate = null;
                }
            }

            return ParseResponse.builder()
                    .amount(parsed.containsKey("amount") ? ((Number) parsed.get("amount")).doubleValue() : null)
                    .currency((String) parsed.getOrDefault("currency", preferredCurrency))
                    .merchant(merchant)
                    .date(parsedDate != null ? parsedDate : LocalDate.now())
                    .note((String) parsed.get("note"))
                    .noteSummary(noteSummary)
                    .suggestedCategoryId(suggestedCategoryId)
                    .confidence(confidence)
                    .source(source)
                    .needsConfirmation(confidence < 0.9)
                    .geminiModel(openRouterConfig.getModel())
                    .safetyWarnings(new ArrayList<>())
                    .build();
        } catch (Exception e) {
            log.error("Error parsing expense: ", e);
            return ParseResponse.builder()
                    .confidence(0.0).source("openrouter").needsConfirmation(true)
                    .geminiModel(openRouterConfig.getModel())
                    .safetyWarnings(List.of("Parse failed, confirmation required"))
                    .build();
        }
    }

    public CategorizeResponse categorize(String firebaseUid, CategorizeRequest request) {
        log.info("Categorizing expense for user: {}", firebaseUid);
        Optional<MemoryEntity> memory = memoryService.getByMerchant(firebaseUid, request.getMerchant());
        if (memory.isPresent() && memory.get().getResolvedCategoryId() != null) {
            return CategorizeResponse.builder()
                    .categoryId(memory.get().getResolvedCategoryId().toString())
                    .confidence(0.95).source("memory")
                    .reason("Matched historical user correction memory.")
                    .needsConfirmation(false).safetyWarnings(new ArrayList<>())
                    .build();
        }

        List<CategoryEntity> categories = categoryService.getCategories(firebaseUid);
        if (categories.isEmpty()) {
            throw AppException.badRequest("No categories available for this user.");
        }

        String categoryList = categories.stream().map(c -> c.getId() + ": " + c.getName())
                .collect(Collectors.joining(", "));
        try {
            String prompt = CATEGORIZE_PROMPT + "\nCategories: " + categoryList +
                    "\nMerchant: " + request.getMerchant() +
                    (request.getNote() != null ? "\nNote: " + request.getNote() : "");
            String response = openRouterService.chat(prompt, "Categorize this expense");
            Map<String, Object> parsed = parseJsonResponse(response);
            String categoryId = (String) parsed.get("categoryId");
            Double confidence = parsed.containsKey("confidence") ? ((Number) parsed.get("confidence")).doubleValue()
                    : 0.7;
            boolean validCategory = categories.stream().anyMatch(c -> c.getId().toString().equals(categoryId));
            if (!validCategory) {
                throw AppException.internalError("AI returned invalid category mapping.");
            }
            return CategorizeResponse.builder()
                    .categoryId(categoryId).confidence(confidence).source("openrouter")
                    .reason((String) parsed.getOrDefault("reason", "OpenRouter categorization result."))
                    .needsConfirmation(confidence < 0.8).safetyWarnings(new ArrayList<>())
                    .build();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error categorizing expense: ", e);
            return CategorizeResponse.builder()
                    .confidence(0.0).source("openrouter").reason("Categorization failed")
                    .needsConfirmation(true).safetyWarnings(List.of("Categorization failed"))
                    .build();
        }
    }

    public CorrectResponse correct(String firebaseUid, CorrectRequest request) {
        log.info("Processing correction for user: {}", firebaseUid);
        Double originalAmount = request.getOriginalAmount() != null ? request.getOriginalAmount().doubleValue() : null;
        Double correctedAmount = request.getCorrectedAmount() != null ? request.getCorrectedAmount().doubleValue()
                : null;
        safetyValidatorService.enforceNoSilentAmountChange(originalAmount, correctedAmount,
                request.getConfirmAmountChange());
        if (request.getExpenseId() != null) {
            correctionService.insertCorrection(firebaseUid, request.getExpenseId(),
                    request.getOriginalCategoryId(), request.getCorrectedCategoryId(),
                    request.getOriginalAmount(), correctedAmount,
                    request.getOriginalMerchant(), request.getCorrectedMerchant());
        }
        String merchantToLearn = request.getCorrectedMerchant() != null ? request.getCorrectedMerchant().trim()
                : request.getMerchant();
        if (merchantToLearn == null || merchantToLearn.isEmpty()) {
            merchantToLearn = request.getMerchant();
        }
        int newOverrideCount = memoryService.applyCorrection(firebaseUid, merchantToLearn,
                request.getCorrectedCategoryId());
        return CorrectResponse.builder()
                .memoryUpdated(true).newOverrideCount(newOverrideCount).confidence(1.0)
                .learningSummary("I will prefer this category for merchant '" + merchantToLearn + "' next time.")
                .learnedMerchant(merchantToLearn).needsConfirmation(false).safetyWarnings(new ArrayList<>())
                .build();
    }

    public ChatResponse chat(String firebaseUid, ChatRequest request) {
        log.info("Processing chat for user: {}", firebaseUid);
        return runChat(firebaseUid, request, null);
    }

    public ChatResponse streamChat(String firebaseUid, ChatRequest request, Consumer<String> deltaConsumer) {
        log.info("Streaming chat for user: {}", firebaseUid);
        return runChat(firebaseUid, request, deltaConsumer);
    }

    private ChatResponse runChat(String firebaseUid, ChatRequest request, Consumer<String> deltaConsumer) {
        safetyValidatorService.enforceNoAutoDelete(request.getQuestion());

        String timezone = request.getTimezone() != null ? request.getTimezone() : "UTC";
        LocalDate today = resolveToday(request);
        String localToday = today.toString();
        String currentMonth = localToday.substring(0, 7);

        List<CategoryEntity> categories = categoryService.getCategories(firebaseUid);
        List<String> categoryNames = categories.stream().map(CategoryEntity::getName).toList();

        AgentDecision rawDecision = aiDecisionService.classify(
                request.getQuestion(), request.getHistory(), categoryNames, localToday, currentMonth);

        AgentValidationResult validation = agentDecisionValidator.validate(rawDecision);
        if (!validation.valid()) {
            log.warn("Agent decision validation failed: {}", validation.violations());
            return ChatResponse.builder()
                    .answer("I need more information to help you with that.")
                    .actionType(AgentActionType.CLARIFY.name())
                    .intent(legacyIntentFromActionType(AgentActionType.CLARIFY))
                    .needsConfirmation(false).safetyWarnings(validation.violations())
                    .build();
        }

        AgentDecision decision = validation.sanitizedDecision();
        AgentPolicyResult policy = agentPolicyService.evaluate(decision);
        if (!policy.allowed()) {
            return ChatResponse.builder()
                    .answer(policy.blockReason())
                    .actionType(AgentActionType.UNSUPPORTED.name())
                    .intent(legacyIntentFromActionType(AgentActionType.UNSUPPORTED))
                    .needsConfirmation(false)
                    .safetyWarnings(List.of(policy.blockReason()))
                    .build();
        }

        return switch (decision.actionType()) {
            case ANSWER_QUESTION -> handleReadAction(
                    firebaseUid, request, decision, categories, today, timezone, deltaConsumer);
            case PREPARE_TRANSACTION, PREPARE_BUDGET, PREPARE_GOAL,
                    PREPARE_CATEGORY, PREPARE_RECURRING_EXPENSE ->
                handleWriteProposal(
                        firebaseUid, decision, policy, request.getQuestion());
            case CLARIFY -> ChatResponse.builder()
                    .answer(decision.userFacingMessage() != null
                            ? decision.userFacingMessage()
                            : "Could you provide more details?")
                    .actionType(AgentActionType.CLARIFY.name())
                    .intent(legacyIntentFromActionType(AgentActionType.CLARIFY))
                    .missingFields(decision.missingFields())
                    .needsConfirmation(false).safetyWarnings(List.of())
                    .build();
            case UNSUPPORTED -> ChatResponse.builder()
                    .answer(decision.userFacingMessage() != null
                            ? decision.userFacingMessage()
                            : "I can't help with that request.")
                    .actionType(AgentActionType.UNSUPPORTED.name())
                    .intent(legacyIntentFromActionType(AgentActionType.UNSUPPORTED))
                    .needsConfirmation(false).safetyWarnings(List.of())
                    .build();
        };
    }

    private ChatResponse handleReadAction(
            String firebaseUid, ChatRequest request, AgentDecision decision,
            List<CategoryEntity> categories, LocalDate today,
            String timezone, Consumer<String> deltaConsumer) {

        List<ExpenseEntity> recentExpenses = expenseService.getExpensesBetween(
                firebaseUid, today.minusDays(90), today);

        String expenseContext = buildExpenseContext(recentExpenses, categories);
        String prompt = String.format(CHAT_PROMPT_TEMPLATE, today, timezone) + "\n" + expenseContext;

        List<Map<String, String>> history = request.getHistory() != null
                ? request.getHistory().stream()
                        .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                        .collect(Collectors.toList())
                : List.of();

        String answer;
        if (!history.isEmpty()) {
            answer = deltaConsumer == null
                    ? openRouterService.chatWithHistory(prompt, history, request.getQuestion())
                    : openRouterService.streamChatWithHistory(prompt, history, request.getQuestion(), deltaConsumer);
        } else {
            answer = deltaConsumer == null
                    ? openRouterService.chat(prompt, request.getQuestion())
                    : openRouterService.streamChat(prompt, request.getQuestion(), deltaConsumer);
        }

        return ChatResponse.builder()
                .answer(answer)
                .queryUsed(request.getQuestion())
                .dataPoints(recentExpenses.size())
                .confidence(0.8)
                .actionType(AgentActionType.ANSWER_QUESTION.name())
                .intent(legacyIntentFromActionType(AgentActionType.ANSWER_QUESTION))
                .silentAction(false)
                .needsConfirmation(false)
                .safetyWarnings(List.of())
                .build();
    }

    private ChatResponse handleWriteProposal(
            String firebaseUid, AgentDecision decision,
            AgentPolicyResult policy, String question) {

        if (decision.proposal() == null) {
            return ChatResponse.builder()
                    .answer(decision.userFacingMessage() != null
                            ? decision.userFacingMessage()
                            : "I couldn't extract the details. Could you rephrase?")
                    .actionType(AgentActionType.CLARIFY.name())
                    .intent(legacyIntentFromActionType(AgentActionType.CLARIFY))
                    .missingFields(decision.missingFields())
                    .needsConfirmation(false).safetyWarnings(List.of())
                    .build();
        }

        UUID pendingId = pendingAiActionService.store(
                firebaseUid, decision.actionType(), decision.proposal());

        String summary = buildProposalSummary(decision);

        return ChatResponse.builder()
                .answer(summary)
                .queryUsed(question)
                .actionType(decision.actionType().name())
                .intent(legacyIntentFromActionType(decision.actionType()))
                .pendingActionId(pendingId.toString())
                .needsConfirmation(true)
                .silentAction(false)
                .safetyWarnings(List.of())
                .missingFields(decision.missingFields())
                .build();
    }

    private String buildProposalSummary(AgentDecision decision) {
        if (decision.userFacingMessage() != null && !decision.userFacingMessage().isBlank()) {
            return decision.userFacingMessage();
        }
        return switch (decision.actionType()) {
            case PREPARE_TRANSACTION -> "I've prepared a transaction for your review.";
            case PREPARE_BUDGET -> "I've prepared a budget for your review.";
            case PREPARE_GOAL -> "I've prepared a savings goal for your review.";
            case PREPARE_CATEGORY -> "I've prepared a category for your review.";
            case PREPARE_RECURRING_EXPENSE -> "I've prepared a recurring expense for your review.";
            default -> "I've prepared an action for your review.";
        };
    }

    private String legacyIntentFromActionType(AgentActionType actionType) {
        return switch (actionType) {
            case ANSWER_QUESTION -> "ask";
            case PREPARE_TRANSACTION -> "add_transaction";
            case PREPARE_BUDGET -> "set_budget";
            case PREPARE_GOAL -> "set_goal";
            case PREPARE_CATEGORY -> "add_category";
            case PREPARE_RECURRING_EXPENSE -> "set_recurring";
            case CLARIFY -> "clarify";
            case UNSUPPORTED -> "unsupported";
        };
    }

    private LocalDate resolveToday(ChatRequest request) {
        if (request.getLocalNowIso() != null && !request.getLocalNowIso().isEmpty()) {
            try {
                return LocalDate.parse(request.getLocalNowIso().substring(0, 10));
            } catch (Exception e) {
                /* fall through */ }
        }
        return LocalDate.now();
    }

    private CategoryEntity resolveCategory(String requested, List<CategoryEntity> categories) {
        if (requested == null || requested.isEmpty())
            return null;
        String lower = requested.toLowerCase().trim();
        for (CategoryEntity c : categories) {
            if (c.getName().equalsIgnoreCase(lower))
                return c;
        }
        for (CategoryEntity c : categories) {
            if (c.getName().toLowerCase().contains(lower) || lower.contains(c.getName().toLowerCase()))
                return c;
        }
        return null;
    }

    private String buildExpenseContext(List<ExpenseEntity> expenses, List<CategoryEntity> categories) {
        if (expenses.isEmpty()) {
            return "Dataset: No expenses recorded in the last 90 days.";
        }
        Map<UUID, String> categoryMap = categories.stream()
                .collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getName, (a, b) -> a));
        StringBuilder sb = new StringBuilder();
        sb.append("Dataset of recent expenses (last 90 days):\n");
        sb.append("Format: date | amount | currency | category | merchant | note\n");
        expenses.stream().limit(50).forEach(e -> {
            String catName = e.getCategoryId() != null ? categoryMap.getOrDefault(e.getCategoryId(), "Uncategorized")
                    : "Uncategorized";
            sb.append(String.format("- %s | $%.2f | %s | %s | %s | %s\n",
                    e.getDate(), e.getAmount(),
                    e.getCurrency() != null ? e.getCurrency() : "USD",
                    catName, e.getMerchant() != null ? e.getMerchant() : "-",
                    e.getNote() != null ? e.getNote() : "-"));
        });
        sb.append("\nAvailable categories: ");
        sb.append(categories.stream().map(CategoryEntity::getName).collect(Collectors.joining(", ")));
        return sb.toString();
    }

    private Map<String, Object> parseJsonResponse(String response) {
        try {
            String cleaned = response.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse JSON response: {}", response);
            return new HashMap<>();
        }
    }
}
