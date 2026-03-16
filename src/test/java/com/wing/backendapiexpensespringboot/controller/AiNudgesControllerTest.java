package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.NudgeActionItem;
import com.wing.backendapiexpensespringboot.dto.NudgeItem;
import com.wing.backendapiexpensespringboot.dto.NudgesResponse;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.NudgeService;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiNudgesController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiNudgesControllerTest {

        private static final String FIREBASE_UID = "firebase-user-1";

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private NudgeService nudgeService;

        @MockitoBean
        private FirebaseAuthFilter firebaseAuthFilter;

        @Test
        void getNudgesReturnsSnakeCaseJsonWithActions() throws Exception {
                OffsetDateTime generatedAt = OffsetDateTime.of(2026, 3, 16, 0, 0, 0, 0, ZoneOffset.UTC);

                when(nudgeService.getNudges(eq(FIREBASE_UID))).thenReturn(
                                NudgesResponse.builder()
                                                .generatedAt(generatedAt)
                                                .nudges(List.of(
                                                                NudgeItem.builder()
                                                                                .id("budget_exceeded_food")
                                                                                .type("budget_exceeded")
                                                                                .title("Budget exceeded")
                                                                                .body("You exceeded your Food budget by $24.00 this month.")
                                                                                .category("Food")
                                                                                .severity("warning")
                                                                                .generatedAt(generatedAt)
                                                                                .actions(List.of(
                                                                                                NudgeActionItem.builder()
                                                                                                                .id("edit-budget")
                                                                                                                .label("Adjust budget")
                                                                                                                .action("edit_budget")
                                                                                                                .build(),
                                                                                                NudgeActionItem.builder()
                                                                                                                .id("view-transactions")
                                                                                                                .label("View transactions")
                                                                                                                .action("view_transactions")
                                                                                                                .build()))
                                                                                .build()))
                                                .needsConfirmation(false)
                                                .safetyWarnings(List.of())
                                                .build());

                mockMvc.perform(get("/ai/nudges").with(authenticatedUser()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.generated_at").value("2026-03-16T00:00:00Z"))
                                .andExpect(jsonPath("$.nudges[0].type").value("budget_exceeded"))
                                .andExpect(jsonPath("$.nudges[0].category").value("Food"))
                                .andExpect(jsonPath("$.nudges[0].generated_at").value("2026-03-16T00:00:00Z"))
                                .andExpect(jsonPath("$.nudges[0].actions[0].label").value("Adjust budget"))
                                .andExpect(jsonPath("$.nudges[0].actions[0].action").value("edit_budget"))
                                .andExpect(jsonPath("$.needs_confirmation").value(false))
                                .andExpect(jsonPath("$.safety_warnings").isArray());

                verify(nudgeService).getNudges(eq(FIREBASE_UID));
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
