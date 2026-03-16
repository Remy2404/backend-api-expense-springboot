package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.FinanceSummaryResponseDto;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.FinanceSummaryService;
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

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class FinanceControllerTest {

        private static final String FIREBASE_UID = "firebase-user-1";

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private FinanceSummaryService financeSummaryService;

        @MockitoBean
        private FirebaseAuthFilter firebaseAuthFilter;

        @Test
        void summaryDelegatesToService() throws Exception {
                when(financeSummaryService.getSummary(eq(FIREBASE_UID), eq("all-time")))
                                .thenReturn(FinanceSummaryResponseDto.builder()
                                                .period("all-time")
                                                .periodStart(null)
                                                .periodEnd(null)
                                                .transactionCount(2)
                                                .totalIncome(30.0)
                                                .totalExpense(10.0)
                                                .balance(20.0)
                                                .build());

                mockMvc.perform(get("/finance/summary")
                                .param("period", "all-time")
                                .with(authenticatedUser()))
                                .andExpect(status().isOk());

                verify(financeSummaryService).getSummary(eq(FIREBASE_UID), eq("all-time"));
        }

        @Test
        void summarySupportsThisMonthPeriod() throws Exception {
                when(financeSummaryService.getSummary(eq(FIREBASE_UID), eq("this-month")))
                                .thenReturn(FinanceSummaryResponseDto.builder()
                                                .period("this-month")
                                                .periodStart(LocalDate.parse("2026-03-01"))
                                                .periodEnd(LocalDate.parse("2026-03-31"))
                                                .transactionCount(3)
                                                .totalIncome(100.0)
                                                .totalExpense(20.0)
                                                .balance(80.0)
                                                .build());

                mockMvc.perform(get("/finance/summary")
                                .param("period", "this-month")
                                .with(authenticatedUser()))
                                .andExpect(status().isOk());

                verify(financeSummaryService).getSummary(eq(FIREBASE_UID), eq("this-month"));
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
