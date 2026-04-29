package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import com.wing.backendapiexpensespringboot.dto.*;
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
import java.util.regex.Pattern;
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

    private static final String CREATE_ENTITY_PROMPT = """
            You are a Smart Personal Finance Assistant.
            Classify create intents and extract structured finance data.
            Return JSON ONLY.

            Valid intents:
            - add_transaction
            - add_budget
            - add_goal
            - add_category
            - add_recurring_expense
            - query_expenses
            - none

            Transaction response:
            {
              "intent": "add_transaction",
              "confidence": 0.0,
              "transactions": [{ "kind": "transaction", "type": "income|expense", "amount": 0.0, "currency": "USD", "category": "", "note": "", "noteSummary": "", "date": "YYYY-MM-DD", "merchant": "" }]
            }

            Budget response:
            {
              "intent": "add_budget",
              "confidence": 0.0,
              "payload": { "kind": "budget", "month": "YYYY-MM", "totalAmount": 0.0 }
            }

            Goal response:
            {
              "intent": "add_goal",
              "confidence": 0.0,
              "payload": { "kind": "goal", "name": "", "targetAmount": 0.0, "currentAmount": 0.0, "deadline": "YYYY-MM-DD", "color": "#10B981", "icon": "target" }
            }

            Category response:
            {
              "intent": "add_category",
              "confidence": 0.0,
              "payload": { "kind": "category", "name": "", "categoryType": "expense|income", "color": "#6366F1", "icon": "tag" }
            }

            Recurring expense response:
            {
              "intent": "add_recurring_expense",
              "confidence": 0.0,
              "payload": { "kind": "recurring_expense", "amount": 0.0, "currency": "USD", "category": "", "note": "", "frequency": "daily|weekly|biweekly|monthly|yearly", "startDate": "YYYY-MM-DD", "endDate": null, "notificationEnabled": true, "notificationDaysBefore": 1 }
            }

            INTENT RULES:
            - add_transaction for new transaction records.
            - add_budget for monthly budget creation.
            - add_goal for savings goal creation.
            - add_category for category creation.
            - add_recurring_expense for recurring bill/subscription creation.
            - query_expenses for querying. none for generic chat.

            EXTRACTION RULES:
            - Detect both income and expenses.
            - If multiple transactions exist, return multiple objects in the transactions array.
            - If only one exists, still return a transactions array with one object.
            - TYPE RULES: use "income" for earned/received/refund/salary/freelance style inflows. Use "expense" for spent/paid/bought style outflows.
            - CURRENCY RULES: default to USD unless another currency is explicitly stated.
            - NOTE RULES: Create a professional title in Title Case (1-3 words). Summarize the item (e.g. "Coffee", "Gasoline"). Do not copy conversational fillers. Fallback to category name if unclear.
            - NOTE_SUMMARY RULES: Create a brief summary of the expense (5-15 words). Include relevant details like who, what, where context. E.g. "Morning coffee at Starbucks with colleague", "Weekly groceries shopping at Walmart", "Gas refill at Shell station". Make it descriptive.
            - MERCHANT RULES: Extract store/brand if available. If not explicit, return empty string.
            - DATE RULES: CURRENT_DATE=%s. Use this for 'today' or if no date is provided.
            - CATEGORY RULES: Use closest match from available categories: %s. Return empty string if missing.
            - AMOUNT RULES: Never guess. Return 0.0 if missing.
            - BUDGET MONTH RULES: if user says this month, use CURRENT_MONTH=%s.
            - GOAL DEFAULTS: if no current amount, return 0.0. If no color/icon, use provided defaults.
            - RECURRING DEFAULTS: if no startDate, use CURRENT_DATE. if no notification setting, return true and 1.
            """;

    private static final Pattern AMOUNT_TOKEN_PATTERN = Pattern.compile("(^|\\s)[$€£¥₹]?\\d+(?:[.,]\\d+)?(\\s|$)");

    public ParseResponse parse(String firebaseUid, ParseRequest request) {
        log.info("Parsing expense for user: {}", firebaseUid);

        String preferredCurrency = "USD";

        String source = "openrouter";
        String merchant = null;
        String suggestedCategoryId = null;

        // Get user's categories for the prompt
        List<CategoryEntity> categories = categoryService.getCategories(firebaseUid);
        String categoryList = categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.joining(", "));

        // Build a prompt that includes available categories
        String parsePromptWithCategories = String.format(
                "You are an expense parser. Parse the following text and extract expense information.\n" +
                "Return JSON with: amount (number), currency (string), merchant (string), date (YYYY-MM-DD), note (string), noteSummary (string), category (string).\n" +
                "If you cannot determine a value, use null.\n" +
                "- note: A professional title in Title Case (1-3 words). E.g. 'Coffee', 'Groceries', 'Gasoline'.\n" +
                "- noteSummary: A brief summary (5-15 words) with relevant details like who, what, where. E.g. 'Morning coffee at Starbucks with colleague', 'Weekly groceries shopping at Walmart'.\n" +
                "Available categories: %s. If the expense doesn't match any category, create a new category name (e.g., 'Drink', 'Snack').",
                categoryList);

        try {
            String response = openRouterService.chat(parsePromptWithCategories, request.getRawText());
            Map<String, Object> parsed = parseJsonResponse(response);

            merchant = (String) parsed.get("merchant");
            String parsedCategory = (String) parsed.get("category");
            String noteSummary = (String) parsed.get("noteSummary");

            // First check memory for merchant-based category
            if (merchant != null && !merchant.isEmpty()) {
                Optional<MemoryEntity> memory = memoryService.getByMerchant(firebaseUid, merchant);
                if (memory.isPresent() && memory.get().getResolvedCategoryId() != null) {
                    suggestedCategoryId = memory.get().getResolvedCategoryId().toString();
                    source = "memory";
                }
            }

            // If no memory match, try to resolve or create category from AI response
            if (suggestedCategoryId == null && parsedCategory != null && !parsedCategory.isEmpty()) {
                CategoryEntity categoryEntity = resolveOrCreateCategory(firebaseUid, parsedCategory, categories);
                if (categoryEntity != null) {
                    suggestedCategoryId = categoryEntity.getId().toString();
                }
            }

            Double confidence = parsed.containsKey("confidence") ? ((Number) parsed.get("confidence")).doubleValue()
                    : 0.8;
            if (suggestedCategoryId != null) {
                confidence = Math.max(confidence, 0.95);
            }

            // Safely parse date - check if key exists AND value is not null
            LocalDate parsedDate = null;
            if (parsed.containsKey("date") && parsed.get("date") != null) {
                try {
                    parsedDate = LocalDate.parse((String) parsed.get("date"));
                } catch (Exception e) {
                    parsedDate = null;
                }
            }

            ParseResponse parseResponse = ParseResponse.builder()
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

            return parseResponse;

        } catch (Exception e) {
            log.error("Error parsing expense: ", e);
            return ParseResponse.builder()
                    .confidence(0.0)
                    .source("openrouter")
                    .needsConfirmation(true)
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
                    .confidence(0.95)
                    .source("memory")
                    .reason("Matched historical user correction memory.")
                    .needsConfirmation(false)
                    .safetyWarnings(new ArrayList<>())
                    .build();
        }

        List<CategoryEntity> categories = categoryService.getCategories(firebaseUid);
        if (categories.isEmpty()) {
            throw AppException.badRequest("No categories available for this user.");
        }

        String categoryList = categories.stream()
                .map(c -> c.getId() + ": " + c.getName())
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

            boolean validCategory = categories.stream()
                    .anyMatch(c -> c.getId().toString().equals(categoryId));

            if (!validCategory) {
                throw AppException.internalError("AI returned invalid category mapping.");
            }

            return CategorizeResponse.builder()
                    .categoryId(categoryId)
                    .confidence(confidence)
                    .source("openrouter")
                    .reason((String) parsed.getOrDefault("reason", "OpenRouter categorization result."))
                    .needsConfirmation(confidence < 0.8)
                    .safetyWarnings(new ArrayList<>())
                    .build();

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error categorizing expense: ", e);
            return CategorizeResponse.builder()
                    .confidence(0.0)
                    .source("openrouter")
                    .reason("Categorization failed")
                    .needsConfirmation(true)
                    .safetyWarnings(List.of("Categorization failed"))
                    .build();
        }
    }

    public CorrectResponse correct(String firebaseUid, CorrectRequest request) {
        log.info("Processing correction for user: {}", firebaseUid);

        // Convert BigDecimal to Double for legacy services
        Double originalAmount = request.getOriginalAmount() != null ? request.getOriginalAmount().doubleValue() : null;
        Double correctedAmount = request.getCorrectedAmount() != null ? request.getCorrectedAmount().doubleValue() : null;

        safetyValidatorService.enforceNoSilentAmountChange(
                originalAmount, correctedAmount, request.getConfirmAmountChange());

        if (request.getExpenseId() != null) {
            correctionService.insertCorrection(
                    firebaseUid,
                    request.getExpenseId(),
                    request.getOriginalCategoryId(),
                    request.getCorrectedCategoryId(),
                    request.getOriginalAmount(),
                    correctedAmount,
                    request.getOriginalMerchant(),
                    request.getCorrectedMerchant());
        }

        String merchantToLearn = request.getCorrectedMerchant() != null ? request.getCorrectedMerchant().trim()
                : request.getMerchant();

        if (merchantToLearn == null || merchantToLearn.isEmpty()) {
            merchantToLearn = request.getMerchant();
        }

        int newOverrideCount = memoryService.applyCorrection(
                firebaseUid, merchantToLearn, request.getCorrectedCategoryId());

        return CorrectResponse.builder()
                .memoryUpdated(true)
                .newOverrideCount(newOverrideCount)
                .confidence(1.0)
                .learningSummary("I will prefer this category for merchant '" + merchantToLearn + "' next time.")
                .learnedMerchant(merchantToLearn)
                .needsConfirmation(false)
                .safetyWarnings(new ArrayList<>())
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
        log.info("Processing chat for user: {}", firebaseUid);

        safetyValidatorService.enforceNoAutoDelete(request.getQuestion());

        // Resolve timezone from request (Bug 7 fix)
        String timezone = request.getTimezone() != null ? request.getTimezone() : "UTC";
        LocalDate today;
        if (request.getLocalNowIso() != null && !request.getLocalNowIso().isEmpty()) {
            try {
                today = LocalDate.parse(request.getLocalNowIso().substring(0, 10));
            } catch (Exception e) {
                today = LocalDate.now();
            }
        } else {
            today = LocalDate.now();
        }
        String localToday = today.toString();

        List<ExpenseEntity> recentExpenses = expenseService.getExpensesBetween(
                firebaseUid, today.minusDays(90), today);

        List<CategoryEntity> categories = categoryService.getCategories(firebaseUid);

        String intent = detectIntent(request.getQuestion());

        List<Map<String, String>> history = request.getHistory().stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        try {
            // Route based on intent (Bug 5 fix — intent-aware routing)
            if (intent.startsWith("add_")) {
                return handleCreateIntent(firebaseUid, request, categories, localToday, recentExpenses.size(), intent);
            }

            // For query_expenses and none intents — answer the question with expense data
            String expenseContext = buildExpenseContext(recentExpenses, categories);
            String prompt = String.format(CHAT_PROMPT_TEMPLATE, localToday, timezone)
                    + "\n" + expenseContext;

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
                    .intent(intent)
                    .silentAction(false)
                    .needsConfirmation(false)
                    .safetyWarnings(new ArrayList<>())
                    .build();

        } catch (Exception e) {
            log.error("Error in chat: ", e);
            return ChatResponse.builder()
                    .answer("I'm having trouble processing your request. Please try again.")
                    .queryUsed(request.getQuestion())
                    .dataPoints(0)
                    .confidence(0.0)
                    .intent("none")
                    .needsConfirmation(true)
                    .safetyWarnings(List.of("Chat processing failed"))
                    .build();
        }
    }

    private ChatResponse handleCreateIntent(
            String firebaseUid, ChatRequest request, List<CategoryEntity> categories,
            String localToday, int dataPoints, String intent) {

        String categoryList = categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.joining(", "));
        String currentMonth = localToday.substring(0, 7);

        String prompt = String.format(CREATE_ENTITY_PROMPT, localToday, categoryList, currentMonth);

        try {
            String response = openRouterService.chat(prompt, request.getQuestion());
            Map<String, Object> parsed = parseJsonResponse(response);
            if ("add_transaction".equals(intent)) {
            List<ChatActionPayload> transactions = extractChatTransactions(
                    firebaseUid,
                    parsed,
                    categories,
                    localToday);
            ChatActionPayload actionPayload = transactions.isEmpty() ? null : transactions.get(0);

            List<String> missingFields = actionPayload == null
                    ? List.of("amount", "category")
                    : findMissingFields(actionPayload);

            String answer = "";
            if (!missingFields.isEmpty()) {
                answer = buildClarifyingAnswer(intent, actionPayload, missingFields, categories);
            } else if (transactions.size() > 1) {
                answer = String.format("Prepared %d transactions from your message.", transactions.size());
            }

            return ChatResponse.builder()
                    .answer(answer)
                    .queryUsed(request.getQuestion())
                    .dataPoints(dataPoints)
                    .confidence(
                            parsed.containsKey("confidence") ? ((Number) parsed.get("confidence")).doubleValue() : 0.8)
                    .intent(intent)
                    .silentAction(true)
                    .payload(actionPayload)
                    .transactions(transactions)
                    .needsConfirmation(false)
                    .safetyWarnings(new ArrayList<>())
                    .build();
            }

            ChatActionPayload actionPayload = buildNonTransactionPayload(
                    firebaseUid,
                    intent,
                    parsed,
                    categories,
                    localToday,
                    currentMonth);
            List<String> missingFields = findMissingFieldsForIntent(intent, actionPayload);
            String answer = missingFields.isEmpty()
                    ? ""
                    : buildClarifyingAnswer(intent, actionPayload, missingFields, categories);

            return ChatResponse.builder()
                    .answer(answer)
                    .queryUsed(request.getQuestion())
                    .dataPoints(dataPoints)
                    .confidence(
                            parsed.containsKey("confidence") ? ((Number) parsed.get("confidence")).doubleValue() : 0.8)
                    .intent(intent)
                    .silentAction(true)
                    .payload(actionPayload)
                    .needsConfirmation(false)
                    .safetyWarnings(new ArrayList<>())
                    .build();

        } catch (Exception e) {
            log.error("Error handling {} intent: ", intent, e);
            return ChatResponse.builder()
                    .answer("I couldn't parse your expense. Could you rephrase?")
                    .queryUsed(request.getQuestion())
                    .dataPoints(dataPoints)
                    .confidence(0.0)
                    .intent(intent)
                    .needsConfirmation(true)
                    .safetyWarnings(List.of("Add expense parsing failed"))
                    .build();
        }
    }

    private String detectIntent(String question) {
        String lower = question.toLowerCase();

        // Query-priority phrases must be checked FIRST (Bug 6 fix)
        if (lower.contains("how much") || lower.contains("total") || lower.contains("show")
                || lower.contains("check") || lower.contains("analyze") || lower.contains("summary")
                || lower.contains("breakdown") || lower.contains("compare") || lower.endsWith("?")) {
            return "query_expenses";
        }

        if (lower.contains("budget")) {
            return "add_budget";
        }

        if (lower.contains("goal") || lower.contains("save up") || lower.contains("saving for")) {
            return "add_goal";
        }

        if (lower.contains("recurring") || lower.contains("every month") || lower.contains("every week")
                || lower.contains("subscription")) {
            return "add_recurring_expense";
        }

        if (lower.contains("category")) {
            return "add_category";
        }

        // Add-transaction signals
        if (lower.contains("add") || lower.contains("record") || lower.contains("log")
                || lower.contains("save") || lower.contains("spent") || lower.contains("paid")
                || lower.contains("income") || lower.contains("expense")
                || lower.contains("earned") || lower.contains("received")
                || lower.contains("salary") || lower.contains("bonus")
                || lower.contains("refund") || lower.contains("reimbursement")) {
            return "add_transaction";
        }

        // Bare transaction statements like "$2.5 entertainment income"
        if (AMOUNT_TOKEN_PATTERN.matcher(lower).find()) {
            return "add_transaction";
        }

        return "none";
    }

    private String buildExpenseContext(List<ExpenseEntity> expenses, List<CategoryEntity> categories) {
        if (expenses.isEmpty()) {
            return "Dataset: No expenses recorded in the last 90 days.";
        }

        Map<UUID, String> categoryMap = categories.stream()
                .collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getName,
                        (existing, replacement) -> existing));

        StringBuilder sb = new StringBuilder();
        sb.append("Dataset of recent expenses (last 90 days):\n");
        sb.append("Format: date | amount | currency | category | merchant | note\n");

        expenses.stream().limit(50).forEach(e -> {
            String categoryName = e.getCategoryId() != null
                    ? categoryMap.getOrDefault(e.getCategoryId(), "Uncategorized")
                    : "Uncategorized";
            sb.append(String.format("- %s | $%.2f | %s | %s | %s | %s\n",
                    e.getDate(),
                    e.getAmount(),
                    e.getCurrency() != null ? e.getCurrency() : "USD",
                    categoryName,
                    e.getMerchant() != null ? e.getMerchant() : "-",
                    e.getNote() != null ? e.getNote() : "-"));
        });

        sb.append("\nAvailable categories: ");
        sb.append(categories.stream().map(CategoryEntity::getName)
                .collect(Collectors.joining(", ")));

        return sb.toString();
    }



    private CategoryEntity resolveOrCreateCategory(String firebaseUid, String requested, List<CategoryEntity> categories) {
        return resolveOrCreateCategory(firebaseUid, requested, categories, CategoryType.EXPENSE);
    }

    private CategoryEntity resolveOrCreateCategory(
            String firebaseUid,
            String requested,
            List<CategoryEntity> categories,
            CategoryType categoryType) {
        if (requested == null || requested.isEmpty())
            return null;

        String lower = requested.toLowerCase().trim();

        // First try to resolve existing category
        for (CategoryEntity c : categories) {
            if (c.getName().equalsIgnoreCase(lower) && matchesCategoryType(c, categoryType)) {
                return c;
            }
        }
        // Partial match
        for (CategoryEntity c : categories) {
            if ((c.getName().toLowerCase().contains(lower) || lower.contains(c.getName().toLowerCase()))
                    && matchesCategoryType(c, categoryType)) {
                return c;
            }
        }

        // If no match found, create a new category
        try {
            log.info("Creating new category '{}' for user {}", requested, firebaseUid);
            CategoryEntity newCategory = categoryService.createCategory(
                    firebaseUid,
                    requested.trim(),
                    "tag",  // default icon
                    "#6366F1", // default color (indigo)
                    categoryType
            );
            categories.add(newCategory);
            return newCategory;
        } catch (Exception e) {
            log.error("Error creating category '{}': {}", requested, e.getMessage());
            return null;
        }
    }

    private boolean matchesCategoryType(CategoryEntity category, CategoryType categoryType) {
        if (categoryType == null) {
            return true;
        }
        return categoryType.name().equalsIgnoreCase(category.getCategoryType());
    }

    @SuppressWarnings("unchecked")
    private List<ChatActionPayload> extractChatTransactions(
            String firebaseUid,
            Map<String, Object> parsed,
            List<CategoryEntity> categories,
            String localToday) {
        List<Map<String, Object>> rawTransactions = new ArrayList<>();
        Object rawList = parsed.get("transactions");
        if (rawList instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> mapItem) {
                    rawTransactions.add((Map<String, Object>) mapItem);
                }
            }
        }

        if (rawTransactions.isEmpty()) {
            Object payload = parsed.get("payload");
            if (payload instanceof Map<?, ?> mapPayload) {
                rawTransactions.add((Map<String, Object>) mapPayload);
            } else if (parsed.containsKey("amount")) {
                rawTransactions.add(parsed);
            }
        }

        return rawTransactions.stream()
                .map(rawTransaction -> buildChatActionPayload(firebaseUid, rawTransaction, categories, localToday))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ChatActionPayload buildChatActionPayload(
            String firebaseUid,
            Map<String, Object> rawTransaction,
            List<CategoryEntity> categories,
            String localToday) {
        if (rawTransaction == null || rawTransaction.isEmpty()) {
            return null;
        }

        String transactionType = normalizeChatTransactionType(rawTransaction.get("type"));
        String category = stringValue(rawTransaction.get("category"));
        CategoryType categoryType = "income".equals(transactionType) ? CategoryType.INCOME : CategoryType.EXPENSE;
        CategoryEntity resolvedCategoryEntity = resolveOrCreateCategory(firebaseUid, category, categories, categoryType);

        Double amount = numberValue(rawTransaction.get("amount"));
        String note = stringValue(rawTransaction.get("note"));
        String noteSummary = stringValue(firstNonNull(rawTransaction.get("noteSummary"), rawTransaction.get("note_summary")));
        String merchant = stringValue(rawTransaction.get("merchant"));
        String date = stringValue(rawTransaction.get("date"));
        String currency = normalizeCurrencyCode(firstNonNull(rawTransaction.get("currency"), rawTransaction.get("currencyCode")));

        return ChatActionPayload.builder()
                .type(transactionType)
                .amount(amount != null && amount > 0 ? amount : null)
                .currency(currency)
                .category(resolvedCategoryEntity != null ? resolvedCategoryEntity.getName() : null)
                .categoryId(resolvedCategoryEntity != null ? resolvedCategoryEntity.getId().toString() : null)
                .note(note)
                .noteSummary(noteSummary)
                .date(date != null && !date.isEmpty() ? date : localToday)
                .merchant(merchant)
                .build();
    }

    private List<String> findMissingFields(ChatActionPayload payload) {
        List<String> missingFields = new ArrayList<>();
        if (payload.getAmount() == null) {
            missingFields.add("amount");
        }
        if (payload.getCategory() == null || payload.getCategory().isEmpty()) {
            missingFields.add("category");
        }
        return missingFields;
    }

    private List<String> findMissingFieldsForIntent(String intent, ChatActionPayload payload) {
        if (payload == null) {
            return switch (intent) {
                case "add_budget" -> List.of("month", "total amount");
                case "add_goal" -> List.of("name", "target amount", "deadline");
                case "add_category" -> List.of("name");
                case "add_recurring_expense" -> List.of("amount", "category", "frequency");
                default -> List.of("details");
            };
        }

        List<String> missingFields = new ArrayList<>();
        switch (intent) {
            case "add_budget" -> {
                if (payload.getMonth() == null || payload.getMonth().isBlank()) missingFields.add("month");
                if (payload.getTotalAmount() == null) missingFields.add("total amount");
            }
            case "add_goal" -> {
                if (payload.getName() == null || payload.getName().isBlank()) missingFields.add("name");
                if (payload.getTargetAmount() == null) missingFields.add("target amount");
                if (payload.getDeadline() == null || payload.getDeadline().isBlank()) missingFields.add("deadline");
            }
            case "add_category" -> {
                if (payload.getName() == null || payload.getName().isBlank()) missingFields.add("name");
            }
            case "add_recurring_expense" -> {
                if (payload.getAmount() == null) missingFields.add("amount");
                if (payload.getCategory() == null || payload.getCategory().isBlank()) missingFields.add("category");
                if (payload.getFrequency() == null || payload.getFrequency().isBlank()) missingFields.add("frequency");
            }
            default -> {
            }
        }
        return missingFields;
    }

    private String buildClarifyingAnswer(
            String intent,
            ChatActionPayload payload,
            List<String> missingFields,
            List<CategoryEntity> categories) {
        if (payload != null
                && missingFields.size() == 1
                && "category".equals(missingFields.get(0))
                && payload.getAmount() != null
                && payload.getAmount() > 0) {
            return buildCategoryClarificationQuestion(intent, payload, categories);
        }

        return "Please share the missing " + String.join(" and ", missingFields)
                + " so I can prepare this " + describeIntent(intent) + ".";
    }

    private String buildCategoryClarificationQuestion(
            String intent,
            ChatActionPayload payload,
            List<CategoryEntity> categories) {
        String itemType = switch (intent) {
            case "add_recurring_expense" -> "recurring expense";
            case "add_transaction" -> "income".equalsIgnoreCase(payload.getType()) ? "income" : "expense";
            default -> describeIntent(intent);
        };

        List<String> suggestions = categories.stream()
                .filter(category -> matchesSuggestedCategoryType(category, payload))
                .map(CategoryEntity::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .filter(name -> !"Other".equalsIgnoreCase(name))
                .distinct()
                .limit(3)
                .collect(Collectors.toCollection(ArrayList::new));

        String otherCategory = categories.stream()
                .map(CategoryEntity::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> "Other".equalsIgnoreCase(name))
                .findFirst()
                .orElse("Other");
        suggestions.add(otherCategory);

        StringBuilder question = new StringBuilder();
        question.append(String.format(
                Locale.US,
                "What category should I use for the $%.2f %s?",
                payload.getAmount(),
                itemType));

        for (int i = 0; i < suggestions.size(); i++) {
            question.append("\n").append(i + 1).append(". ").append(suggestions.get(i));
        }

        return question.toString();
    }

    private boolean matchesSuggestedCategoryType(CategoryEntity category, ChatActionPayload payload) {
        if (category == null) {
            return false;
        }

        String expectedType = payload.getCategoryType();
        if (expectedType == null || expectedType.isBlank()) {
            expectedType = payload.getType();
        }
        if (expectedType == null || expectedType.isBlank()) {
            expectedType = "expense";
        }

        String categoryType = category.getCategoryType();
        return categoryType == null || categoryType.equalsIgnoreCase(expectedType);
    }

    private String describeIntent(String intent) {
        return switch (intent) {
            case "add_budget" -> "budget";
            case "add_goal" -> "goal";
            case "add_category" -> "category";
            case "add_recurring_expense" -> "recurring expense";
            case "add_transaction" -> "transaction";
            default -> "item";
        };
    }

    @SuppressWarnings("unchecked")
    private ChatActionPayload buildNonTransactionPayload(
            String firebaseUid,
            String intent,
            Map<String, Object> parsed,
            List<CategoryEntity> categories,
            String localToday,
            String currentMonth) {
        Object payloadValue = parsed.get("payload");
        Map<String, Object> payload = payloadValue instanceof Map<?, ?> mapPayload
                ? (Map<String, Object>) mapPayload
                : parsed;

        return switch (intent) {
            case "add_budget" -> ChatActionPayload.builder()
                    .kind("budget")
                    .month(stringValue(payload.get("month")) != null ? stringValue(payload.get("month")) : currentMonth)
                    .totalAmount(numberValue(firstNonNull(payload.get("totalAmount"), payload.get("total_amount"))))
                    .build();
            case "add_goal" -> ChatActionPayload.builder()
                    .kind("goal")
                    .name(stringValue(payload.get("name")))
                    .targetAmount(numberValue(firstNonNull(payload.get("targetAmount"), payload.get("target_amount"))))
                    .currentAmount(numberValue(firstNonNull(payload.get("currentAmount"), payload.get("current_amount"))) != null
                            ? numberValue(firstNonNull(payload.get("currentAmount"), payload.get("current_amount")))
                            : 0.0)
                    .deadline(stringValue(payload.get("deadline")) != null ? stringValue(payload.get("deadline")) : localToday)
                    .color(stringValue(payload.get("color")) != null ? stringValue(payload.get("color")) : "#10B981")
                    .icon(stringValue(payload.get("icon")) != null ? stringValue(payload.get("icon")) : "target")
                    .build();
            case "add_category" -> ChatActionPayload.builder()
                    .kind("category")
                    .name(stringValue(payload.get("name")))
                    .categoryType(normalizeChatTransactionType(firstNonNull(payload.get("categoryType"), payload.get("type"))))
                    .color(stringValue(payload.get("color")) != null ? stringValue(payload.get("color")) : "#6366F1")
                    .icon(stringValue(payload.get("icon")) != null ? stringValue(payload.get("icon")) : "tag")
                    .build();
            case "add_recurring_expense" -> {
                String category = stringValue(payload.get("category"));
                String type = normalizeChatTransactionType(firstNonNull(payload.get("categoryType"), payload.get("type")));
                CategoryType categoryType = "income".equals(type) ? CategoryType.INCOME : CategoryType.EXPENSE;
                CategoryEntity resolvedCategoryEntity = resolveOrCreateCategory(firebaseUid, category, categories, categoryType);
                yield ChatActionPayload.builder()
                        .kind("recurring_expense")
                        .type(type)
                        .amount(numberValue(payload.get("amount")))
                        .currency(normalizeCurrencyCode(payload.get("currency")))
                        .category(resolvedCategoryEntity != null ? resolvedCategoryEntity.getName() : null)
                        .categoryId(resolvedCategoryEntity != null ? resolvedCategoryEntity.getId().toString() : null)
                        .note(stringValue(payload.get("note")))
                        .frequency(stringValue(payload.get("frequency")) != null ? stringValue(payload.get("frequency")) : "monthly")
                        .startDate(stringValue(firstNonNull(payload.get("startDate"), payload.get("start_date"))) != null
                                ? stringValue(firstNonNull(payload.get("startDate"), payload.get("start_date")))
                                : localToday)
                        .endDate(stringValue(firstNonNull(payload.get("endDate"), payload.get("end_date"))))
                        .notificationEnabled(booleanValue(payload.get("notificationEnabled"), true))
                        .notificationDaysBefore(integerValue(payload.get("notificationDaysBefore"), 1))
                        .build();
            }
            default -> null;
        };
    }

    private Object firstNonNull(Object primary, Object secondary) {
        return primary != null ? primary : secondary;
    }

    private String stringValue(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue).trim();
        return value.isEmpty() ? null : value;
    }

    private Double numberValue(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number.doubleValue();
        }
        if (rawValue == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(rawValue).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeChatTransactionType(Object rawValue) {
        String value = stringValue(rawValue);
        return value != null && value.equalsIgnoreCase("income") ? "income" : "expense";
    }

    private String normalizeCurrencyCode(Object rawValue) {
        String value = stringValue(rawValue);
        return value == null ? "USD" : value.toUpperCase(Locale.ROOT);
    }

    private Boolean booleanValue(Object rawValue, boolean fallback) {
        if (rawValue instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (rawValue == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(rawValue).trim());
    }

    private Integer integerValue(Object rawValue, int fallback) {
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        if (rawValue == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(rawValue).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Map<String, Object> parseJsonResponse(String response) {
        try {
            String cleaned = response.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON response: {}", response);
            return new HashMap<>();
        }
    }
}
