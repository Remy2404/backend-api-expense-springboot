package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRouterService {

    @Qualifier("openRouterRestTemplate")
    private final RestTemplate restTemplate;
    private final OpenRouterConfig openRouterConfig;
    private final ObjectMapper objectMapper;

    public String chat(String systemPrompt, String userMessage) {
        log.debug("Calling OpenRouter with model: {}", openRouterConfig.getModel());

        try {
            String url = openRouterConfig.getBaseUrl() + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openRouterConfig.getApiKey());

            String requestBody = String.format("""
                    {
                        "model": "%s",
                        "messages": [
                            {"role": "system", "content": "%s"},
                            {"role": "user", "content": "%s"}
                        ],
                        "temperature": 0.7
                    }
                    """,
                    openRouterConfig.getModel(),
                    escapeJson(systemPrompt),
                    escapeJson(userMessage));

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                return choices.get(0).get("message").get("content").asText();
            }

            return "";
        } catch (Exception e) {
            log.error("Error calling OpenRouter: ", e);
            return "{\"error\": \"Failed to process request\"}";
        }
    }

    public String chatWithHistory(String systemPrompt, List<Map<String, String>> messages, String currentQuestion) {
        log.debug("Calling OpenRouter with history, model: {}", openRouterConfig.getModel());

        try {
            String url = openRouterConfig.getBaseUrl() + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openRouterConfig.getApiKey());

            StringBuilder messagesJson = new StringBuilder();
            messagesJson.append("{\"role\": \"system\", \"content\": \"").append(escapeJson(systemPrompt))
                    .append("\"}");

            for (Map<String, String> msg : messages) {
                messagesJson.append(",{\"role\": \"").append(msg.get("role"))
                        .append("\", \"content\": \"").append(escapeJson(msg.get("content"))).append("\"}");
            }

            // Always append the current user question as the last message
            messagesJson.append(",{\"role\": \"user\", \"content\": \"").append(escapeJson(currentQuestion))
                    .append("\"}");

            String requestBody = String.format("""
                    {
                        "model": "%s",
                        "messages": [%s],
                        "temperature": 0.7
                    }
                    """, openRouterConfig.getModel(), messagesJson);

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                return choices.get(0).get("message").get("content").asText();
            }

            return "";
        } catch (Exception e) {
            log.error("Error calling OpenRouter with history: ", e);
            return "{\"error\": \"Failed to process request\"}";
        }
    }

    private String escapeJson(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
