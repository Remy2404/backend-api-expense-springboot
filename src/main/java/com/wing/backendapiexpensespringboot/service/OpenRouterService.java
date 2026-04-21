package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRouterService {

    // Security Fix: Allowlist of valid OpenRouter base URLs to prevent SSRF
    private static final String ALLOWED_BASE_URL = "https://openrouter.ai/api/v1";

    @Qualifier("openRouterRestTemplate")
    private final RestTemplate restTemplate;
    private final OpenRouterConfig openRouterConfig;
    private final ObjectMapper objectMapper;

    /**
     * Security Fix: Validate OpenRouter base URL on startup to prevent SSRF attacks.
     * Only allows the official OpenRouter API endpoint.
     */
    @PostConstruct
    public void validateConfiguration() {
        String baseUrl = openRouterConfig.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("OpenRouter base URL is not configured");
        }

        String normalizedUrl = baseUrl.trim();
        if (!normalizedUrl.equals(ALLOWED_BASE_URL)) {
            throw new IllegalStateException(
                    "Invalid OpenRouter base URL. Must be: " + ALLOWED_BASE_URL +
                    " (got: " + normalizedUrl + ")"
            );
        }

        log.info("OpenRouter configuration validated successfully");
    }

    public String chat(String systemPrompt, String userMessage) {
        log.debug("Calling OpenRouter with model: {}", openRouterConfig.getModel());

        try {
            String url = openRouterConfig.getBaseUrl() + "/chat/completions";
            String requestBody = objectMapper.writeValueAsString(buildRequestPayload(
                    systemPrompt,
                    List.of(),
                    userMessage,
                    false));
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers());
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
            String requestBody = objectMapper.writeValueAsString(buildRequestPayload(
                    systemPrompt,
                    messages,
                    currentQuestion,
                    false));
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers());
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

    public String streamChat(String systemPrompt, String userMessage, Consumer<String> deltaConsumer) {
        return streamChatWithHistory(systemPrompt, List.of(), userMessage, deltaConsumer);
    }

    public String streamChatWithHistory(
            String systemPrompt,
            List<Map<String, String>> messages,
            String currentQuestion,
            Consumer<String> deltaConsumer) {
        log.debug("Streaming OpenRouter chat with history, model: {}", openRouterConfig.getModel());

        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(openRouterConfig.getBaseUrl() + "/chat/completions");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(openRouterConfig.getTimeout() * 1_000);
            connection.setReadTimeout(openRouterConfig.getTimeout() * 1_000);
            connection.setDoOutput(true);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterConfig.getApiKey());
            connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);

            String requestBody = objectMapper.writeValueAsString(buildRequestPayload(
                    systemPrompt,
                    messages,
                    currentQuestion,
                    true));

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new IllegalStateException("OpenRouter stream request failed with status " + status
                        + ": " + readResponseBody(connection.getErrorStream()));
            }

            StringBuilder answer = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }

                    String payload = line.substring("data:".length()).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }

                    JsonNode root = objectMapper.readTree(payload);
                    JsonNode choices = root.path("choices");
                    if (!choices.isArray() || choices.isEmpty()) {
                        continue;
                    }

                    JsonNode delta = choices.get(0).path("delta");
                    String textDelta = extractContentDelta(delta);
                    if (textDelta == null || textDelta.isBlank()) {
                        continue;
                    }

                    answer.append(textDelta);
                    deltaConsumer.accept(textDelta);
                }
            }

            return answer.toString();
        } catch (Exception e) {
            log.error("Error streaming OpenRouter chat: ", e);
            return "{\"error\": \"Failed to process request\"}";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openRouterConfig.getApiKey());
        return headers;
    }

    private Map<String, Object> buildRequestPayload(
            String systemPrompt,
            List<Map<String, String>> messages,
            String currentQuestion,
            boolean stream) {
        List<Map<String, String>> payloadMessages = new java.util.ArrayList<>();
        payloadMessages.add(Map.of("role", "system", "content", safeText(systemPrompt)));
        for (Map<String, String> message : messages) {
            payloadMessages.add(Map.of(
                    "role", safeText(message.get("role")),
                    "content", safeText(message.get("content"))));
        }
        payloadMessages.add(Map.of("role", "user", "content", safeText(currentQuestion)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openRouterConfig.getModel());
        payload.put("messages", payloadMessages);
        payload.put("temperature", 0.7);
        payload.put("stream", stream);
        return payload;
    }

    private String extractContentDelta(JsonNode delta) {
        JsonNode content = delta.get("content");
        if (content == null || content.isNull()) {
            return null;
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            content.forEach(item -> {
                JsonNode text = item.get("text");
                if (text != null && text.isTextual()) {
                    builder.append(text.asText());
                }
            });
            return builder.toString();
        }
        return null;
    }

    private String readResponseBody(InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
