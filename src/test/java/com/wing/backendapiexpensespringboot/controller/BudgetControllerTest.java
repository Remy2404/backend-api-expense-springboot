package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.BudgetSummaryResponseDto;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.BudgetQueryService;
import com.wing.backendapiexpensespringboot.service.BudgetSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BudgetController.class)
@AutoConfigureMockMvc(addFilters = false)
class BudgetControllerTest {

    private static final String FIREBASE_UID = "firebase-user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BudgetQueryService budgetQueryService;

    @MockitoBean
    private BudgetSummaryService budgetSummaryService;

    @MockitoBean
    private FirebaseAuthFilter firebaseAuthFilter;

    @Test
    void budgetSummaryDelegatesToService() throws Exception {
        when(budgetSummaryService.getBudgetSummary(eq(FIREBASE_UID), eq("2026-03")))
                .thenReturn(BudgetSummaryResponseDto.builder()
                        .budgetLimit(500.0)
                        .spent(125.0)
                        .remaining(375.0)
                        .build());

        mockMvc.perform(get("/budgets/summary")
                .param("month", "2026-03")
                .with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(budgetSummaryService).getBudgetSummary(eq(FIREBASE_UID), eq("2026-03"));
    }

    private RequestPostProcessor authenticatedUser() {
        UserPrincipal principal = UserPrincipal.builder()
                .firebaseUid(FIREBASE_UID)
                .role("USER")
                .build();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        return request -> {
            SecurityContextHolder.setContext(context);
            return request;
        };
    }
}
