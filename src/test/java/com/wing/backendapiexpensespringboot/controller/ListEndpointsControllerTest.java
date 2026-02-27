package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.service.BudgetQueryService;
import com.wing.backendapiexpensespringboot.service.InsightQueryService;
import com.wing.backendapiexpensespringboot.service.RecurringExpenseQueryService;
import com.wing.backendapiexpensespringboot.service.SavingsGoalQueryService;
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

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        BudgetController.class,
        SavingsGoalController.class,
        RecurringExpenseController.class,
        InsightController.class
})
@AutoConfigureMockMvc(addFilters = false)
class ListEndpointsControllerTest {

    private static final String FIREBASE_UID = "firebase-user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BudgetQueryService budgetQueryService;

    @MockBean
    private SavingsGoalQueryService savingsGoalQueryService;

    @MockBean
    private RecurringExpenseQueryService recurringExpenseQueryService;

    @MockBean
    private InsightQueryService insightQueryService;

    @MockBean
    private FirebaseAuthFilter firebaseAuthFilter;

    @Test
    void listBudgetsUsesDefaultPaginationAndArchiveFilter() throws Exception {
        when(budgetQueryService.getBudgets(FIREBASE_UID, 0, 50, false)).thenReturn(List.of());

        mockMvc.perform(get("/budgets").with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(budgetQueryService).getBudgets(FIREBASE_UID, 0, 50, false);
    }

    @Test
    void listGoalsUsesExplicitQueryParams() throws Exception {
        when(savingsGoalQueryService.getGoals(FIREBASE_UID, 10, 25, true)).thenReturn(List.of());

        mockMvc.perform(
                        get("/goals")
                                .param("offset", "10")
                                .param("limit", "25")
                                .param("includeArchived", "true")
                                .with(authenticatedUser())
                )
                .andExpect(status().isOk());

        verify(savingsGoalQueryService).getGoals(FIREBASE_UID, 10, 25, true);
    }

    @Test
    void listRecurringUsesExplicitQueryParams() throws Exception {
        when(recurringExpenseQueryService.getRecurringExpenses(FIREBASE_UID, 5, 10, true)).thenReturn(List.of());

        mockMvc.perform(
                        get("/recurring-expenses")
                                .param("offset", "5")
                                .param("limit", "10")
                                .param("includeArchived", "true")
                                .with(authenticatedUser())
                )
                .andExpect(status().isOk());

        verify(recurringExpenseQueryService).getRecurringExpenses(FIREBASE_UID, 5, 10, true);
    }

    @Test
    void listInsightsUsesDefaultPagination() throws Exception {
        when(insightQueryService.getInsights(FIREBASE_UID, 0, 50)).thenReturn(List.of());

        mockMvc.perform(get("/insights").with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(insightQueryService).getInsights(FIREBASE_UID, 0, 50);
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
