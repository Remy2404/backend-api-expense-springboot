package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.repository.AiChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatSessionServiceTest {

    @Mock
    private AiOrchestratorService aiOrchestratorService;
    @Mock
    private AiChatMessageRepository aiChatMessageRepository;
    @Mock
    private RealtimeRelayService realtimeRelayService;

    @InjectMocks
    private AiChatSessionService aiChatSessionService;

    @Test
    void clearHistory_shouldDeleteAllMessagesForUser() {
        when(aiChatMessageRepository.deleteByFirebaseUid("uid-1")).thenReturn(6L);

        long deletedCount = aiChatSessionService.clearHistory("uid-1");

        assertThat(deletedCount).isEqualTo(6L);
        verify(aiChatMessageRepository).deleteByFirebaseUid("uid-1");
    }
}
