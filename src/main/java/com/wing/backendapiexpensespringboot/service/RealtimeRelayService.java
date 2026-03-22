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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeRelayService {

    private final RestTemplate restTemplate;
    private final RealtimeConfig realtimeConfig;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return realtimeConfig.normalizedRelayUrl() != null
                && !realtimeConfig.normalizedRelayUrl().isBlank()
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

    public void publishChatStart(String firebaseUid, String requestId) {
        if (!isEnabled() || requestId == null || requestId.isBlank()) {
            return;
        }

        publishChatEvent("ai.chat.start", firebaseUid, requestId, Map.of("requestId", requestId));
    }

    public void publishChatDelta(String firebaseUid, String requestId, String delta) {
        if (!isEnabled()
                || requestId == null
                || requestId.isBlank()
                || delta == null
                || delta.isBlank()) {
            return;
        }

        publishChatEvent("ai.chat.delta", firebaseUid, requestId, Map.of(
                "requestId", requestId,
                "delta", delta));
    }

    public void publishChatComplete(String firebaseUid, String requestId, ChatResponse response) {
        if (!isEnabled() || requestId == null || requestId.isBlank()) {
            return;
        }

        publishChatEvent("ai.chat.complete", firebaseUid, requestId, Map.of(
                "requestId", requestId,
                "response", objectToMap(response)));
    }

    private void publishChatEvent(String type, String firebaseUid, String requestId, Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("firebaseUid", firebaseUid);
        payload.put("requestId", requestId);
        payload.putAll(body);
        post(payload);
    }

    private Map<String, Object> objectToMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    private void post(Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(realtimeConfig.getRelaySecret());
            restTemplate.postForEntity(realtimeConfig.normalizedRelayUrl() + "/internal/events",
                    new HttpEntity<>(payload, headers), Void.class);
        } catch (Exception exception) {
            log.warn("Failed to publish realtime relay event: {}", exception.getMessage());
        }
    }
}
