package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.AiChatSessionService;
import com.wing.backendapiexpensespringboot.service.AiPendingActionExecutionService;
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

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiChatControllerTest {

    private static final String FIREBASE_UID = "firebase-user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiChatSessionService aiChatSessionService;

    @MockitoBean
    private AiPendingActionExecutionService aiPendingActionExecutionService;

    @MockitoBean
    private FirebaseAuthFilter firebaseAuthFilter;

    @Test
    void confirmActionReturnsNoContentAndDelegatesToExecutionService() throws Exception {
        UUID actionId = UUID.randomUUID();

        mockMvc.perform(post("/ai/chat/actions/{actionId}/confirm", actionId).with(authenticatedUser()))
                .andExpect(status().isNoContent());

        verify(aiPendingActionExecutionService).confirmAndExecute(FIREBASE_UID, actionId);
    }

    @Test
    void cancelActionReturnsNoContentAndDelegatesToExecutionService() throws Exception {
        UUID actionId = UUID.randomUUID();

        mockMvc.perform(post("/ai/chat/actions/{actionId}/cancel", actionId).with(authenticatedUser()))
                .andExpect(status().isNoContent());

        verify(aiPendingActionExecutionService).cancel(FIREBASE_UID, actionId);
    }

    @Test
    void confirmActionReturnsUnauthorizedWhenUserIsMissing() throws Exception {
        UUID actionId = UUID.randomUUID();

        mockMvc.perform(post("/ai/chat/actions/{actionId}/confirm", actionId))
                .andExpect(status().isUnauthorized());
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
