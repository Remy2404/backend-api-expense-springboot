package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.DashboardSummaryResponseDto;
import com.wing.backendapiexpensespringboot.dto.FinanceSummaryResponseDto;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.DashboardSummaryService;
import com.wing.backendapiexpensespringboot.service.FinanceSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardSummaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardSummaryControllerTest {

        private static final String FIREBASE_UID = "firebase-user-1";

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private DashboardSummaryService dashboardSummaryService;

        @MockitoBean
        private FinanceSummaryService financeSummaryService;

        @MockitoBean
        private FirebaseAuthFilter firebaseAuthFilter;

        @Test
        void dashboardSummaryDelegatesToDashboardSummaryService() throws Exception {
                when(dashboardSummaryService.getSummary(eq(FIREBASE_UID)))
                                .thenReturn(DashboardSummaryResponseDto.builder()
                                                .totalIncome(100.0)
                                                .totalExpense(40.0)
                                                .balance(60.0)
                                                .transactionCount(10)
                                                .monthlyIncome(30.0)
                                                .monthlyExpense(20.0)
                                                .build());

                mockMvc.perform(get("/dashboard/summary").with(authenticatedUser()))
                                .andExpect(status().isOk());

                verify(dashboardSummaryService).getSummary(eq(FIREBASE_UID));
        }

        @Test
        void legacyDashboardSummaryStillDelegatesToFinanceService() throws Exception {
                when(financeSummaryService.getSummary(eq(FIREBASE_UID), eq("all-time")))
                                .thenReturn(FinanceSummaryResponseDto.builder()
                                                .period("all-time")
                                                .periodStart(null)
                                                .periodEnd(null)
                                                .transactionCount(10)
                                                .totalIncome(100.0)
                                                .totalExpense(40.0)
                                                .balance(60.0)
                                                .build());

                mockMvc.perform(get("/dashboard-summary").with(authenticatedUser()))
                                .andExpect(status().isOk());

                verify(financeSummaryService).getSummary(eq(FIREBASE_UID), eq("all-time"));
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
