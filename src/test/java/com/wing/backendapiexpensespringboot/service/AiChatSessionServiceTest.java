package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ChatRequest;
import com.wing.backendapiexpensespringboot.dto.ChatResponse;
import com.wing.backendapiexpensespringboot.repository.AiChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void streamChat_shouldPublishRealtimeEventsWhileGenerating() {
        ChatRequest request = ChatRequest.builder()
                .question("How much did I spend this week?")
                .requestId("req-1")
                .build();
        ChatResponse streamedResponse = ChatResponse.builder()
                .answer("Total spent is $93.60.")
                .intent("query_expenses")
                .build();
        when(aiChatMessageRepository.findByFirebaseUidOrderByCreatedAtDesc(eq("uid-1"), any()))
                .thenReturn(Page.empty());

        when(aiOrchestratorService.streamChat(eq("uid-1"), any(ChatRequest.class), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> deltaConsumer = invocation.getArgument(2);
                    deltaConsumer.accept("Total spent ");
                    deltaConsumer.accept("is $93.60.");
                    return streamedResponse;
                });

        ChatResponse response = aiChatSessionService.streamChat("uid-1", request);

        assertThat(response).isEqualTo(streamedResponse);
        verify(realtimeRelayService).publishUserMessage("uid-1", "req-1", "How much did I spend this week?");
        verify(realtimeRelayService).publishChatStart("uid-1", "req-1");
        verify(realtimeRelayService).publishChatDelta("uid-1", "req-1", "Total spent ");
        verify(realtimeRelayService).publishChatDelta("uid-1", "req-1", "is $93.60.");
        verify(realtimeRelayService).publishChatComplete("uid-1", "req-1", streamedResponse);
    }
}
