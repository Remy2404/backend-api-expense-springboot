package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.ExpenseListItemDto;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.ExpenseFilterQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExpenseControllerTest {

    private static final String FIREBASE_UID = "firebase-user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseFilterQueryService expenseFilterQueryService;

    @MockBean
    private FirebaseAuthFilter firebaseAuthFilter;

    @Test
    void listExpensesPassesFiltersToService() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(expenseFilterQueryService.getFilteredExpenses(
                FIREBASE_UID,
                5,
                10,
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-27"),
                categoryId,
                "coffee",
                1.0,
                20.0
        )).thenReturn(List.of(ExpenseListItemDto.builder().build()));

        mockMvc.perform(get("/expenses")
                        .param("offset", "5")
                        .param("limit", "10")
                        .param("dateFrom", "2026-02-01")
                        .param("dateTo", "2026-02-27")
                        .param("categoryId", categoryId.toString())
                        .param("merchant", "coffee")
                        .param("minAmount", "1.0")
                        .param("maxAmount", "20.0")
                        .with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(expenseFilterQueryService).getFilteredExpenses(
                FIREBASE_UID,
                5,
                10,
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-27"),
                categoryId,
                "coffee",
                1.0,
                20.0
        );
    }

    @Test
    void exportPdfEndpointRemoved() throws Exception {
        mockMvc.perform(get("/expenses/export/pdf").with(authenticatedUser()))
                .andExpect(status().isNotFound());
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
