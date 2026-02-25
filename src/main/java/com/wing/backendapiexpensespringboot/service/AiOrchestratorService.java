package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import com.wing.backendapiexpensespringboot.dto.*;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.model.MemoryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
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

    private static final String PARSE_PROMPT = """
            You are an expense parser. Parse the following text and extract expense information.
            Return JSON with: amount (number), currency (string), merchant (string), date (YYYY-MM-DD), note (string).
            If you cannot determine a value, use null.
            """;

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

    private static final String ADD_EXPENSE_PROMPT = """
            You are a Smart Personal Finance Assistant.
            Classify intent and extract expense data.
            Return JSON ONLY: { "intent": "add_expense|query_expenses|none", "confidence": 0.0, "payload": { "amount": 0.0, "category": "", "note": "", "noteSummary": "", "date": "YYYY-MM-DD", "merchant": "" } }

            INTENT RULES:
            - add_expense for new records. query_expenses for querying. none for generic chat.

            EXTRACTION RULES FOR ADD_EXPENSE:
            - NOTE RULES: Create a professional title in Title Case (1-3 words). Summarize the item (e.g. "Coffee", "Gasoline"). Do not copy conversational fillers. Fallback to category name if unclear.
            - NOTE_SUMMARY RULES: Create a brief summary of the expense (5-15 words). Include relevant details like who, what, where context. E.g. "Morning coffee at Starbucks with colleague", "Weekly groceries shopping at Walmart", "Gas refill at Shell station". Make it descriptive.
            - MERCHANT RULES: Extract store/brand if available. If not explicit, return empty string.
            - DATE RULES: CURRENT_DATE=%s. Use this for 'today' or if no date is provided.
            - CATEGORY RULES: Use closest match from available categories: %s. Return empty string if missing.
            - AMOUNT RULES: Never guess. Return 0.0 if missing.
            """;

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
            if ("add_expense".equals(intent)) {
                return handleAddExpenseIntent(firebaseUid, request, categories, localToday, recentExpenses.size());
            }

            // For query_expenses and none intents — answer the question with expense data
            String expenseContext = buildExpenseContext(recentExpenses, categories);
            String prompt = String.format(CHAT_PROMPT_TEMPLATE, localToday, timezone)
                    + "\n" + expenseContext;

            String answer;
            if (!history.isEmpty()) {
                answer = openRouterService.chatWithHistory(prompt, history, request.getQuestion());
            } else {
                answer = openRouterService.chat(prompt, request.getQuestion());
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

    private ChatResponse handleAddExpenseIntent(
            String firebaseUid, ChatRequest request, List<CategoryEntity> categories,
            String localToday, int dataPoints) {

        String categoryList = categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.joining(", "));

        String prompt = String.format(ADD_EXPENSE_PROMPT, localToday, categoryList);

        try {
            String response = openRouterService.chat(prompt, request.getQuestion());
            Map<String, Object> parsed = parseJsonResponse(response);

            Map<String, Object> payload = parsed.containsKey("payload") ? (Map<String, Object>) parsed.get("payload")
                    : parsed;

            Double amount = payload.containsKey("amount") && payload.get("amount") != null
                    ? ((Number) payload.get("amount")).doubleValue()
                    : null;
            String category = (String) payload.getOrDefault("category", "");
            String note = (String) payload.getOrDefault("note", "");
            String noteSummary = (String) payload.getOrDefault("noteSummary", "");
            String date = (String) payload.getOrDefault("date", localToday);
            String merchant = (String) payload.getOrDefault("merchant", "");

            // Resolve category name against user's actual categories
            // If no match found, create a new category
            CategoryEntity resolvedCategoryEntity = resolveOrCreateCategory(firebaseUid, category, categories);

            ChatActionPayload actionPayload = ChatActionPayload.builder()
                    .amount(amount != null && amount > 0 ? amount : null)
                    .category(resolvedCategoryEntity != null ? resolvedCategoryEntity.getName() : null)
                    .categoryId(resolvedCategoryEntity != null ? resolvedCategoryEntity.getId().toString() : null)
                    .note(note != null && !note.isEmpty() ? note : null)
                    .noteSummary(noteSummary != null && !noteSummary.isEmpty() ? noteSummary : null)
                    .date(date != null && !date.isEmpty() ? date : localToday)
                    .merchant(merchant != null && !merchant.isEmpty() ? merchant : null)
                    .build();

            // Check for missing required fields
            List<String> missingFields = new ArrayList<>();
            if (actionPayload.getAmount() == null)
                missingFields.add("amount");
            if (actionPayload.getCategory() == null || actionPayload.getCategory().isEmpty())
                missingFields.add("category");

            String answer = "";
            if (!missingFields.isEmpty()) {
                answer = "Please share the missing " + String.join(" and ", missingFields)
                        + " so I can prepare this expense.";
            }

            return ChatResponse.builder()
                    .answer(answer)
                    .queryUsed(request.getQuestion())
                    .dataPoints(dataPoints)
                    .confidence(
                            parsed.containsKey("confidence") ? ((Number) parsed.get("confidence")).doubleValue() : 0.8)
                    .intent("add_expense")
                    .silentAction(true)
                    .payload(actionPayload)
                    .needsConfirmation(false)
                    .safetyWarnings(new ArrayList<>())
                    .build();

        } catch (Exception e) {
            log.error("Error handling add_expense intent: ", e);
            return ChatResponse.builder()
                    .answer("I couldn't parse your expense. Could you rephrase?")
                    .queryUsed(request.getQuestion())
                    .dataPoints(dataPoints)
                    .confidence(0.0)
                    .intent("add_expense")
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

        // Add-expense signals
        if (lower.contains("add") || lower.contains("record") || lower.contains("log")
                || lower.contains("save") || lower.contains("spent") || lower.contains("paid")) {
            return "add_expense";
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

    private String resolveCategory(String requested, List<CategoryEntity> categories) {
        if (requested == null || requested.isEmpty())
            return null;
        String lower = requested.toLowerCase().trim();

        // Exact match first
        for (CategoryEntity c : categories) {
            if (c.getName().equalsIgnoreCase(lower))
                return c.getName();
        }
        // Partial match
        for (CategoryEntity c : categories) {
            if (c.getName().toLowerCase().contains(lower) || lower.contains(c.getName().toLowerCase())) {
                return c.getName();
            }
        }
        return null;
    }

    private CategoryEntity resolveOrCreateCategory(String firebaseUid, String requested, List<CategoryEntity> categories) {
        if (requested == null || requested.isEmpty())
            return null;

        String lower = requested.toLowerCase().trim();

        // First try to resolve existing category
        for (CategoryEntity c : categories) {
            if (c.getName().equalsIgnoreCase(lower)) {
                return c;
            }
        }
        // Partial match
        for (CategoryEntity c : categories) {
            if (c.getName().toLowerCase().contains(lower) || lower.contains(c.getName().toLowerCase())) {
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
                    "#6366F1" // default color (indigo)
            );
            return newCategory;
        } catch (Exception e) {
            log.error("Error creating category '{}': {}", requested, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseJsonResponse(String response) {
        try {
            String cleaned = response.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(cleaned, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse JSON response: {}", response);
            return new HashMap<>();
        }
    }
}
