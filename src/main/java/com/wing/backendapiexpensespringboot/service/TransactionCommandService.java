package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.CreateTransactionRequest;
import com.wing.backendapiexpensespringboot.dto.CreateTransactionResponse;
import com.wing.backendapiexpensespringboot.dto.TransactionItemDto;
import com.wing.backendapiexpensespringboot.exception.AppException;
import com.wing.backendapiexpensespringboot.model.CategoryEntity;
import com.wing.backendapiexpensespringboot.model.CategoryType;
import com.wing.backendapiexpensespringboot.model.TransactionEntity;
import com.wing.backendapiexpensespringboot.model.TransactionType;
import com.wing.backendapiexpensespringboot.model.WalletEntity;
import com.wing.backendapiexpensespringboot.repository.TransactionRepository;
import com.wing.backendapiexpensespringboot.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionCommandService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final CategoryService categoryService;

    @Transactional
    public CreateTransactionResponse createTransaction(
            String firebaseUid,
            CreateTransactionRequest request
    ) {
        TransactionType transactionType = parseTransactionType(request.getTransactionType());
        CategoryEntity category = validateCategory(firebaseUid, request, transactionType);
        TransactionEntity existing = findIdempotentTransaction(firebaseUid, request.getIdempotencyKey());
        if (existing != null) {
            return buildResponse(firebaseUid, existing);
        }

        WalletEntity wallet = walletRepository.findByFirebaseUidForUpdate(firebaseUid)
                .orElseGet(() -> walletRepository.save(WalletEntity.builder()
                        .firebaseUid(firebaseUid)
                        .balance(BigDecimal.ZERO)
                        .updatedAt(LocalDateTime.now())
                        .build()));

        BigDecimal signedDelta = transactionType == TransactionType.INCOME
                ? request.getAmount()
                : request.getAmount().negate();

        TransactionEntity created = transactionRepository.save(TransactionEntity.builder()
                .firebaseUid(firebaseUid)
                .amount(request.getAmount())
                .currency(request.getCurrency() == null || request.getCurrency().isBlank()
                        ? "USD" : request.getCurrency())
                .transactionType(transactionType.name())
                .categoryId(request.getCategoryId())
                .note(request.getNote())
                .date(request.getDate() == null ? LocalDate.now() : request.getDate())
                .idempotencyKey(request.getIdempotencyKey())
                .createdAt(LocalDateTime.now())
                .build());

        wallet.setBalance(wallet.getBalance().add(signedDelta));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        return buildResponse(firebaseUid, created);
    }

    private CreateTransactionResponse buildResponse(String firebaseUid, TransactionEntity transaction) {
        TransactionRepository.TotalsView totals = transactionRepository.getTotalsByFirebaseUid(firebaseUid);
        BigDecimal totalIncome = totals != null && totals.getTotalIncome() != null
                ? totals.getTotalIncome()
                : BigDecimal.ZERO;
        BigDecimal totalExpense = totals != null && totals.getTotalExpense() != null
                ? totals.getTotalExpense()
                : BigDecimal.ZERO;
        BigDecimal currentBalance = totalIncome.subtract(totalExpense);

        return CreateTransactionResponse.builder()
                .transaction(TransactionItemDto.builder()
                        .id(transaction.getId())
                        .amount(transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .transactionType(transaction.getTransactionType())
                        .categoryId(transaction.getCategoryId())
                        .note(transaction.getNote())
                        .date(transaction.getDate())
                        .createdAt(transaction.getCreatedAt())
                        .build())
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .currentBalance(currentBalance)
                .build();
    }

    private TransactionEntity findIdempotentTransaction(String firebaseUid, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return transactionRepository.findByFirebaseUidAndIdempotencyKey(firebaseUid, idempotencyKey)
                .orElse(null);
    }

    private CategoryEntity validateCategory(
            String firebaseUid,
            CreateTransactionRequest request,
            TransactionType transactionType
    ) {
        CategoryEntity category = categoryService.getCategoryById(firebaseUid, request.getCategoryId());
        if (category == null) {
            throw AppException.badRequest("categoryId is invalid");
        }

        String rawType = category.getCategoryType();
        CategoryType categoryType = CategoryType.EXPENSE;
        if (rawType != null && !rawType.isBlank()) {
            try {
                categoryType = CategoryType.valueOf(rawType.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                categoryType = CategoryType.EXPENSE;
            }
        }

        if (categoryType.name().equals(transactionType.name())) {
            return category;
        }
        throw AppException.badRequest("category type does not match transaction type");
    }

    private TransactionType parseTransactionType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw AppException.badRequest("transactionType is required");
        }
        try {
            return TransactionType.valueOf(rawType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw AppException.badRequest("transactionType must be EXPENSE or INCOME");
        }
    }
}
