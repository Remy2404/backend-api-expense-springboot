package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.ExpenseEntity;
import com.wing.backendapiexpensespringboot.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
}
