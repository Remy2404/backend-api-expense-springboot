package com.wing.backendapiexpensespringboot.controller;

import com.wing.backendapiexpensespringboot.dto.SyncPullResponseDto;
import com.wing.backendapiexpensespringboot.dto.SyncPushRequestDto;
import com.wing.backendapiexpensespringboot.dto.SyncPushResponseDto;
import com.wing.backendapiexpensespringboot.security.FirebaseAuthFilter;
import com.wing.backendapiexpensespringboot.security.UserPrincipal;
import com.wing.backendapiexpensespringboot.service.RealtimeRelayService;
import com.wing.backendapiexpensespringboot.service.SyncService;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
@AutoConfigureMockMvc(addFilters = false)
class SyncControllerTest {

    private static final String FIREBASE_UID = "firebase-user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SyncService syncService;

    @MockBean
    private FirebaseAuthFilter firebaseAuthFilter;

    @MockBean
    private RealtimeRelayService realtimeRelayService;

    @Test
    void pushDelegatesToService() throws Exception {
        when(syncService.push(eq(FIREBASE_UID), any(SyncPushRequestDto.class)))
                .thenReturn(SyncPushResponseDto.empty());

        mockMvc.perform(post("/sync/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(syncService).push(eq(FIREBASE_UID), any(SyncPushRequestDto.class));
    }

    @Test
    void pushAcceptsExplicitNullCollections() throws Exception {
        when(syncService.push(eq(FIREBASE_UID), any(SyncPushRequestDto.class)))
                .thenReturn(SyncPushResponseDto.empty());

        mockMvc.perform(post("/sync/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expenses": null,
                                  "categories": null,
                                  "budgets": null,
                                  "goals": null,
                                  "recurring": null
                                }
                                """)
                        .with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(syncService).push(eq(FIREBASE_UID), any(SyncPushRequestDto.class));
    }

    @Test
    void pullDelegatesToService() throws Exception {
        when(syncService.pull(
                eq(FIREBASE_UID),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC)),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 1, 0, 0, ZoneOffset.UTC)),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 2, 0, 0, ZoneOffset.UTC)),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 3, 0, 0, ZoneOffset.UTC)),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 4, 0, 0, ZoneOffset.UTC))
        )).thenReturn(SyncPullResponseDto.empty());

        mockMvc.perform(get("/sync/pull")
                        .param("expense_since", "2026-03-01T10:00:00Z")
                        .param("category_since", "2026-03-01T10:01:00Z")
                        .param("budget_since", "2026-03-01T10:02:00Z")
                        .param("goal_since", "2026-03-01T10:03:00Z")
                        .param("recurring_since", "2026-03-01T10:04:00Z")
                        .with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(syncService).pull(
                eq(FIREBASE_UID),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC)),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 1, 0, 0, ZoneOffset.UTC)),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 2, 0, 0, ZoneOffset.UTC)),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 3, 0, 0, ZoneOffset.UTC)),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 4, 0, 0, ZoneOffset.UTC))
        );
    }

    @Test
    void pullNormalizesOffsetDateTimeToUtc() throws Exception {
        when(syncService.pull(
                eq(FIREBASE_UID),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC)),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(SyncPullResponseDto.empty());

        mockMvc.perform(get("/sync/pull")
                        .param("expense_since", "2026-03-01T17:00:00+07:00")
                        .with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(syncService).pull(
                eq(FIREBASE_UID),
                eq(OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC)),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        );
    }

    @Test
    void pullReturnsBadRequestForInvalidDate() throws Exception {
        mockMvc.perform(get("/sync/pull")
                        .param("expense_since", "invalid")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest());
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
