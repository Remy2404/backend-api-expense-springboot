package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ExpenseMutationRequestDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import com.wing.backendapiexpensespringboot.service.media.ImageKitMediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ImageKitMediaService imageKitMediaService;

    public List<ExpenseEntity> getExpenses(String firebaseUid) {
        return expenseRepository.findByFirebaseUidOrderByDateDesc(firebaseUid);
    }

    public List<ExpenseEntity> getExpensesBetween(String firebaseUid, LocalDate start, LocalDate end) {
        return expenseRepository.findByFirebaseUidAndDateBetweenOrderByDateDesc(
                firebaseUid,
                startOfDayUtc(start),
                startOfNextDayUtc(end));
    }

    public List<ExpenseEntity> getExpensesChangedSince(String firebaseUid, OffsetDateTime since) {
        return expenseRepository.findChangedSince(firebaseUid, since);
    }

    public ExpenseEntity getExpenseById(String firebaseUid, UUID expenseId) {
        return expenseRepository.findById(expenseId)
                .filter(e -> e.getFirebaseUid().equals(firebaseUid))
                .orElseThrow(() -> AppException.notFound("Expense not found"));
    }

    @Transactional
    public ExpenseEntity createExpense(String firebaseUid, Map<String, Object> data) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ExpenseEntity expense = ExpenseEntity.builder()
                .firebaseUid(firebaseUid)
                .amount((Double) data.get("amount"))
                .transactionType((String) data.getOrDefault("transactionType", "EXPENSE"))
                .currency((String) data.getOrDefault("currency", "USD"))
                .merchant((String) data.get("merchant"))
                .date(resolveExpenseDateValue(data.get("date")))
                .note((String) data.get("note"))
                .noteSummary((String) data.get("noteSummary"))
                .categoryId((UUID) data.get("categoryId"))
                .createdAt(now)
                .updatedAt(now)
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

        OffsetDateTime createdAt = parseDateTimeOrNow(request.getClientCreatedAt());
        ExpenseEntity expense = ExpenseEntity.builder()
                .id(clientId)
                .firebaseUid(firebaseUid)
                .amount(requireAmount(request.getAmount()))
                .transactionType(normalizeTransactionType(request.getTransactionType()))
                .currency(normalizeCurrency(request.getCurrency()))
                .merchant(request.getMerchant())
                .date(parseExpenseDateOrNow(request.getDate()))
                .note(request.getNotes())
                .noteSummary(request.getNoteSummary())
                .categoryId(parseUuidOrNull(request.getCategoryId()))
                .recurringExpenseId(parseUuidOrNull(request.getRecurringExpenseId()))
                .receiptPaths(imageKitMediaService.normalizeIncomingReceiptPaths(firebaseUid, request.getReceiptPaths()))
                .originalAmount(toBigDecimalNullable(request.getOriginalAmount()))
                .exchangeRate(toBigDecimalNullable(request.getExchangeRate()))
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
            expense.setDate(parseExpenseDateOrNow(request.getDate()));
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
            expense.setReceiptPaths(imageKitMediaService.normalizeIncomingReceiptPaths(firebaseUid, request.getReceiptPaths()));
        }
        if (request.getOriginalAmount() != null) {
            expense.setOriginalAmount(toBigDecimalNullable(request.getOriginalAmount()));
        }
        if (request.getExchangeRate() != null) {
            expense.setExchangeRate(toBigDecimalNullable(request.getExchangeRate()));
        }
        if (request.getRateSource() != null) {
            expense.setRateSource(request.getRateSource());
        }

        expense.setUpdatedAt(nowUtc());
        return expenseRepository.save(expense);
    }

    @Transactional
    public ExpenseEntity softDeleteExpense(String firebaseUid, UUID expenseId) {
        ExpenseEntity expense = getExpenseById(firebaseUid, expenseId);
        OffsetDateTime now = nowUtc();
        expense.setIsDeleted(true);
        expense.setDeletedAt(now);
        expense.setUpdatedAt(now);
        return expenseRepository.save(expense);
    }

    @Transactional
    public ExpenseEntity updateAiCategorization(
            String firebaseUid,
            UUID expenseId,
            UUID aiCategoryId,
            Double aiConfidence,
            String aiSource) {
        ExpenseEntity expense = getExpenseById(firebaseUid, expenseId);
        expense.setAiCategoryId(aiCategoryId);
        expense.setAiConfidence(aiConfidence);
        expense.setAiSource(aiSource);
        expense.setUpdatedAt(nowUtc());
        return expenseRepository.save(expense);
    }

    public Double getTotalBetween(String firebaseUid, LocalDate start, LocalDate end) {
        Double total = expenseRepository.sumAmountByFirebaseUidAndDateBetween(
                firebaseUid,
                startOfDayUtc(start),
                startOfNextDayUtc(end));
        return total != null ? total : 0.0;
    }

    public List<ExpenseEntity> getByCategory(String firebaseUid, UUID categoryId) {
        return expenseRepository.findByFirebaseUidAndCategoryId(firebaseUid, categoryId);
    }

    public List<ExpenseEntity> getByCategoryBetween(String firebaseUid, UUID categoryId,
                                                    LocalDate start, LocalDate end) {
        return expenseRepository.findByFirebaseUidAndCategoryIdAndDateBetween(
                firebaseUid,
                categoryId,
                startOfDayUtc(start),
                startOfNextDayUtc(end));
    }

    private OffsetDateTime parseExpenseDateOrNow(String raw) {
        if (raw == null || raw.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        try {
            return LocalDate.parse(raw).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return normalizeToUtc(OffsetDateTime.parse(raw));
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        throw AppException.badRequest("date must be ISO-8601 date or datetime");
    }

    private OffsetDateTime resolveExpenseDateValue(Object rawValue) {
        if (rawValue == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (rawValue instanceof OffsetDateTime offsetDateTime) {
            return normalizeToUtc(offsetDateTime);
        }
        if (rawValue instanceof LocalDate localDate) {
            return localDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        }
        if (rawValue instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        return parseExpenseDateOrNow(String.valueOf(rawValue));
    }

    private OffsetDateTime parseDateTimeOrNow(String raw) {
        if (raw == null || raw.isBlank()) {
            return nowUtc();
        }
        try {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        return nowUtc();
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

    private BigDecimal toBigDecimalNullable(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime startOfNextDayUtc(LocalDate date) {
        return date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime normalizeToUtc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
