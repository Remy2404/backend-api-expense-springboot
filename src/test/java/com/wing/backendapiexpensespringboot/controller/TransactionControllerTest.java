package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.CreateTransactionRequest;
import com.wing.backendapiexpensespringboot.dto.CreateTransactionResponse;
import com.wing.backendapiexpensespringboot.dto.TransactionItemDto;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.TransactionCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

    private static final String FIREBASE_UID = "firebase-user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionCommandService transactionCommandService;

    @MockBean
    private FirebaseAuthFilter firebaseAuthFilter;

    @Test
    void createTransactionDelegatesToService() throws Exception {
        UUID categoryId = UUID.randomUUID();
        CreateTransactionResponse response = CreateTransactionResponse.builder()
                .transaction(TransactionItemDto.builder()
                        .id(UUID.randomUUID())
                        .amount(new BigDecimal("12.50"))
                        .currency("USD")
                        .transactionType("EXPENSE")
                        .categoryId(categoryId)
                        .note("Coffee")
                        .date(LocalDate.parse("2026-03-02"))
                        .createdAt(LocalDateTime.parse("2026-03-02T10:00:00"))
                        .build())
                .totalIncome(new BigDecimal("200.00"))
                .totalExpense(new BigDecimal("12.50"))
                .currentBalance(new BigDecimal("187.50"))
                .build();

        when(transactionCommandService.createTransaction(eq(FIREBASE_UID), any(CreateTransactionRequest.class)))
                .thenReturn(response);

        String requestBody = String.format("""
                {
                  "amount": 12.5,
                  "transactionType": "EXPENSE",
                  "categoryId": "%s",
                  "note": "Coffee",
                  "currency": "USD",
                  "idempotencyKey": "txn-001"
                }
                """, categoryId);

        mockMvc.perform(post("/transactions")
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(transactionCommandService).createTransaction(eq(FIREBASE_UID), any(CreateTransactionRequest.class));
    }

    private RequestPostProcessor authenticatedUser() {
        UserPrincipal principal = UserPrincipal.builder()
                .firebaseUid(FIREBASE_UID)
                .role("USER")
                .build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        return request -> {
            SecurityContextHolder.setContext(context);
            return request;
        };
    }
}
