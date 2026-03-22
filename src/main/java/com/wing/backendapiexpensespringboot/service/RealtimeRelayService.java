package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.RealtimeConfig;
import com.wing.backendapiexpensespringboot.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeRelayService {

    private static final int CHAT_CHUNK_SIZE = 48;

    private final RestTemplate restTemplate;
    private final RealtimeConfig realtimeConfig;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return realtimeConfig.getRelayUrl() != null
                && !realtimeConfig.getRelayUrl().isBlank()
                && realtimeConfig.getRelaySecret() != null
                && !realtimeConfig.getRelaySecret().isBlank();
    }

    public void publishSyncInvalidation(String firebaseUid, List<String> entities, String reason) {
        if (!isEnabled() || entities == null || entities.isEmpty()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "sync.updated");
        payload.put("firebaseUid", firebaseUid);
        payload.put("reason", reason);
        payload.put("entities", entities);
        post(payload);
    }

    public void publishUserMessage(String firebaseUid, String requestId, String message) {
        if (!isEnabled()
                || requestId == null
                || requestId.isBlank()
                || message == null
                || message.isBlank()) {
            return;
        }

        publishChatEvent("ai.chat.user", firebaseUid, requestId, Map.of(
                "requestId", requestId,
                "message", message));
    }

    public void streamChatResponse(String firebaseUid, String requestId, ChatResponse response) {
        if (!isEnabled() || requestId == null || requestId.isBlank()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            publishChatEvent("ai.chat.start", firebaseUid, requestId, Map.of("requestId", requestId));

            String answer = response.getAnswer() == null ? "" : response.getAnswer();
            if (!answer.isBlank()) {
                for (String chunk : chunk(answer)) {
                    publishChatEvent("ai.chat.delta", firebaseUid, requestId, Map.of(
                            "requestId", requestId,
                            "delta", chunk));
                }
            }

            publishChatEvent("ai.chat.complete", firebaseUid, requestId, Map.of(
                    "requestId", requestId,
                    "response", objectToMap(response)));
        }).exceptionally(error -> {
            log.warn("Failed to stream chat response to realtime relay: {}", error.getMessage());
            return null;
        });
    }

    private void publishChatEvent(String type, String firebaseUid, String requestId, Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("firebaseUid", firebaseUid);
        payload.put("requestId", requestId);
        payload.putAll(body);
        post(payload);
    }

    private List<String> chunk(String content) {
        if (content.length() <= CHAT_CHUNK_SIZE) {
            return List.of(content);
        }

        ArrayList<String> chunks = new ArrayList<>();
        int index = 0;
        while (index < content.length()) {
            int end = Math.min(content.length(), index + CHAT_CHUNK_SIZE);
            chunks.add(content.substring(index, end));
            index = end;
        }
        return chunks;
    }

    private Map<String, Object> objectToMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    private void post(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(realtimeConfig.getRelaySecret());
            restTemplate.postForEntity(realtimeConfig.getRelayUrl() + "/internal/events",
                    new HttpEntity<>(payload, headers), Void.class);
        } catch (Exception exception) {
            log.warn("Failed to publish realtime relay event: {}", exception.getMessage());
        }
    }
}
