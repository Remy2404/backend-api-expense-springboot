package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.dto.ChatHistoryItemDto;
import com.wing.backendapiexpensespringboot.dto.ChatHistoryMessage;
import com.wing.backendapiexpensespringboot.dto.ChatRequest;
import com.wing.backendapiexpensespringboot.dto.ChatResponse;
import com.wing.backendapiexpensespringboot.model.AiChatMessageEntity;
import com.wing.backendapiexpensespringboot.repository.AiChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiChatSessionService {

    private static final int DEFAULT_CONTEXT_LIMIT = 12;

    private final AiOrchestratorService aiOrchestratorService;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final RealtimeRelayService realtimeRelayService;

    @Transactional(readOnly = true)
    public List<ChatHistoryItemDto> getHistory(String firebaseUid, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return aiChatMessageRepository
                .findByFirebaseUidOrderByCreatedAtDesc(firebaseUid, PageRequest.of(0, safeLimit))
                .stream()
                .sorted(Comparator.comparing(AiChatMessageEntity::getCreatedAt))
                .map(this::toHistoryItem)
                .toList();
    }

    @Transactional
    public ChatResponse chat(String firebaseUid, ChatRequest request) {
        ChatRequest effectiveRequest = buildEffectiveRequest(firebaseUid, request);

        String userContent = normalizeUserContent(request);
        if (!userContent.isBlank()) {
            saveMessage(firebaseUid, "user", userContent, request.getRequestId());
        }

        ChatResponse response = aiOrchestratorService.chat(firebaseUid, effectiveRequest);
        if (response.getAnswer() != null && !response.getAnswer().isBlank()) {
            saveMessage(firebaseUid, "assistant", response.getAnswer(), request.getRequestId());
        }
        return response;
    }

    public ChatResponse streamChat(String firebaseUid, ChatRequest request) {
        ChatResponse response = chat(firebaseUid, request);
        realtimeRelayService.streamChatResponse(firebaseUid, request.getRequestId(), response);
        return response;
    }

    private ChatRequest buildEffectiveRequest(String firebaseUid, ChatRequest request) {
        List<ChatHistoryMessage> requestHistory = request.getHistory() == null ? List.of() : request.getHistory();
        if (!requestHistory.isEmpty()) {
            return request;
        }

        List<ChatHistoryMessage> persistedHistory = getHistory(firebaseUid, DEFAULT_CONTEXT_LIMIT).stream()
                .map(item -> ChatHistoryMessage.builder()
                        .role(item.getRole())
                        .content(item.getContent())
                        .build())
                .toList();

        return ChatRequest.builder()
                .question(request.getQuestion())
                .history(persistedHistory)
                .timezone(request.getTimezone())
                .localNowIso(request.getLocalNowIso())
                .imagePresent(request.getImagePresent())
                .attachmentBase64(request.getAttachmentBase64())
                .attachmentMime(request.getAttachmentMime())
                .requestId(request.getRequestId())
                .build();
    }

    private String normalizeUserContent(ChatRequest request) {
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (!question.isBlank()) {
            return question;
        }
        return Boolean.TRUE.equals(request.getImagePresent()) ? "Sent an attachment" : "";
    }

    private void saveMessage(String firebaseUid, String role, String content, String requestId) {
        aiChatMessageRepository.save(AiChatMessageEntity.builder()
                .firebaseUid(firebaseUid)
                .role(role)
                .content(content)
                .requestId(requestId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private ChatHistoryItemDto toHistoryItem(AiChatMessageEntity entity) {
        return ChatHistoryItemDto.builder()
                .id(entity.getId())
                .role(entity.getRole())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
