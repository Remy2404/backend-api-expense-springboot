package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ExpenseMutationRequestDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public List<ExpenseEntity> getExpenses(String firebaseUid) {
        return expenseRepository.findByFirebaseUidOrderByDateDesc(firebaseUid);
    }

    public List<ExpenseEntity> getExpensesBetween(String firebaseUid, LocalDate start, LocalDate end) {
        return expenseRepository.findByFirebaseUidAndDateBetweenOrderByDateDesc(firebaseUid, start, end);
    }

    public List<ExpenseEntity> getExpensesChangedSince(String firebaseUid, LocalDateTime since) {
        return expenseRepository.findChangedSince(firebaseUid, since);
    }

    public ExpenseEntity getExpenseById(String firebaseUid, UUID expenseId) {
        return expenseRepository.findById(expenseId)
                .filter(e -> e.getFirebaseUid().equals(firebaseUid))
                .orElseThrow(() -> AppException.notFound("Expense not found"));
    }

    @Transactional
    public ExpenseEntity createExpense(String firebaseUid, Map<String, Object> data) {
        ExpenseEntity expense = ExpenseEntity.builder()
                .firebaseUid(firebaseUid)
                .amount((Double) data.get("amount"))
                .transactionType((String) data.getOrDefault("transactionType", "EXPENSE"))
                .currency((String) data.getOrDefault("currency", "USD"))
                .merchant((String) data.get("merchant"))
                .date((LocalDate) data.getOrDefault("date", LocalDate.now()))
                .note((String) data.get("note"))
                .noteSummary((String) data.get("noteSummary"))
                .categoryId((UUID) data.get("categoryId"))
                .createdAt(LocalDateTime.now())
                .build();

        return expenseRepository.save(expense);
    }

    @Transactional
    public ExpenseEntity createExpense(String firebaseUid, ExpenseMutationRequestDto request) {
        UUID clientId = parseUuidOrNull(request.getClientId());
        if (clientId != null) {
            ExpenseEntity existing = findOwnedByClientId(firebaseUid, clientId);
            if (existing != null) {
                return existing;
            }
        }

        LocalDateTime createdAt = parseDateTimeOrNow(request.getClientCreatedAt());
        ExpenseEntity expense = ExpenseEntity.builder()
                .id(clientId)
                .firebaseUid(firebaseUid)
                .amount(requireAmount(request.getAmount()))
                .transactionType(normalizeTransactionType(request.getTransactionType()))
                .currency(normalizeCurrency(request.getCurrency()))
                .merchant(request.getMerchant())
                .date(parseDateOrToday(request.getDate()))
                .note(request.getNotes())
                .noteSummary(request.getNoteSummary())
                .categoryId(parseUuidOrNull(request.getCategoryId()))
                .recurringExpenseId(parseUuidOrNull(request.getRecurringExpenseId()))
                .receiptPaths(request.getReceiptPaths())
                .originalAmount(request.getOriginalAmount())
                .exchangeRate(request.getExchangeRate())
                .rateSource(request.getRateSource())
                .isDeleted(false)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();

        try {
            return expenseRepository.save(expense);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
            if (clientId == null) {
                throw ex;
            }
            // Concurrent create with same clientId can race. Treat as idempotent create.
            ExpenseEntity existing = findOwnedByClientId(firebaseUid, clientId);
            if (existing != null) {
                return existing;
            }
            throw ex;
        }
    }

    @Transactional
    public ExpenseEntity updateExpense(String firebaseUid, UUID expenseId, ExpenseMutationRequestDto request) {
        ExpenseEntity expense = getExpenseById(firebaseUid, expenseId);
        if (request.getAmount() != null) {
            expense.setAmount(request.getAmount());
        }
        if (request.getTransactionType() != null) {
            expense.setTransactionType(normalizeTransactionType(request.getTransactionType()));
        }
        if (request.getCurrency() != null) {
            expense.setCurrency(normalizeCurrency(request.getCurrency()));
        }
        if (request.getMerchant() != null) {
            expense.setMerchant(request.getMerchant());
        }
        if (request.getDate() != null && !request.getDate().isBlank()) {
            expense.setDate(parseDateOrToday(request.getDate()));
        }
        if (request.getNotes() != null) {
            expense.setNote(request.getNotes());
        }
        if (request.getNoteSummary() != null) {
            expense.setNoteSummary(request.getNoteSummary());
        }
        if (request.getCategoryId() != null) {
            expense.setCategoryId(parseUuidOrNull(request.getCategoryId()));
        }
        if (request.getRecurringExpenseId() != null) {
            expense.setRecurringExpenseId(parseUuidOrNull(request.getRecurringExpenseId()));
        }
        if (request.getReceiptPaths() != null) {
            expense.setReceiptPaths(request.getReceiptPaths());
        }
        if (request.getOriginalAmount() != null) {
            expense.setOriginalAmount(request.getOriginalAmount());
        }
        if (request.getExchangeRate() != null) {
            expense.setExchangeRate(request.getExchangeRate());
        }
        if (request.getRateSource() != null) {
            expense.setRateSource(request.getRateSource());
        }

        expense.setUpdatedAt(nowUtcLocal());
        return expenseRepository.save(expense);
    }

    @Transactional
    public ExpenseEntity softDeleteExpense(String firebaseUid, UUID expenseId) {
        ExpenseEntity expense = getExpenseById(firebaseUid, expenseId);
        LocalDateTime now = nowUtcLocal();
        expense.setIsDeleted(true);
        expense.setDeletedAt(now);
        expense.setUpdatedAt(now);
        return expenseRepository.save(expense);
    }

    @Transactional
    public ExpenseEntity updateAiCategorization(String firebaseUid, UUID expenseId, UUID aiCategoryId,
                                                 Double aiConfidence, String aiSource) {
        ExpenseEntity expense = getExpenseById(firebaseUid, expenseId);
        expense.setAiCategoryId(aiCategoryId);
        expense.setAiConfidence(aiConfidence);
        expense.setAiSource(aiSource);
        expense.setUpdatedAt(LocalDateTime.now());
        return expenseRepository.save(expense);
    }

    public Double getTotalBetween(String firebaseUid, LocalDate start, LocalDate end) {
        Double total = expenseRepository.sumAmountByFirebaseUidAndDateBetween(firebaseUid, start, end);
        return total != null ? total : 0.0;
    }

    public List<ExpenseEntity> getByCategory(String firebaseUid, UUID categoryId) {
        return expenseRepository.findByFirebaseUidAndCategoryId(firebaseUid, categoryId);
    }

    public List<ExpenseEntity> getByCategoryBetween(String firebaseUid, UUID categoryId,
                                                     LocalDate start, LocalDate end) {
        return expenseRepository.findByFirebaseUidAndCategoryIdAndDateBetween(firebaseUid, categoryId, start, end);
    }

    private LocalDate parseDateOrToday(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(raw)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (Exception ignored) {
        }
        throw AppException.badRequest("date must be ISO-8601 date or datetime");
    }

    private LocalDateTime parseDateTimeOrNow(String raw) {
        if (raw == null || raw.isBlank()) {
            return nowUtcLocal();
        }
        try {
            return OffsetDateTime.parse(raw)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ignored) {
        }
        return nowUtcLocal();
    }

    private UUID parseUuidOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw AppException.badRequest("Invalid UUID value");
        }
    }

    private ExpenseEntity findOwnedByClientId(String firebaseUid, UUID clientId) {
        ExpenseEntity existing = expenseRepository.findById(clientId).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!firebaseUid.equals(existing.getFirebaseUid())) {
            throw AppException.unauthorized("Expense does not belong to current user");
        }
        return existing;
    }

    private String normalizeTransactionType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "EXPENSE";
        }
        return "INCOME".equalsIgnoreCase(raw) ? "INCOME" : "EXPENSE";
    }

    private String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return "USD";
        }
        return raw.trim().toUpperCase();
    }

    private Double requireAmount(Double amount) {
        if (amount == null) {
            throw AppException.badRequest("amount is required");
        }
        return amount;
    }

    private LocalDateTime nowUtcLocal() {
        return OffsetDateTime.now(java.time.ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
