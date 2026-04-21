package com.wing.backendapiexpensespringboot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wing.backendapiexpensespringboot.config.OpenRouterConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenRouterServiceUrlValidationTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OpenRouterConfig openRouterConfig;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OpenRouterService openRouterService;

    @Test
    void validOfficialOpenRouterUrlIsAccepted() {
        when(openRouterConfig.getBaseUrl()).thenReturn("https://openrouter.ai/api/v1");

        assertThatCode(() -> openRouterService.validateConfiguration())
                .doesNotThrowAnyException();
    }

    @Test
    void nullBaseUrlIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn(null);

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void emptyBaseUrlIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void whitespaceOnlyBaseUrlIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("   ");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void maliciousUrlIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("https://evil.com/api/v1");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }

    @Test
    void localhostUrlIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("http://localhost:8080/api/v1");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }

    @Test
    void internalNetworkUrlIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("http://192.168.1.100/api/v1");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }

    @Test
    void urlWithTrailingSlashIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("https://openrouter.ai/api/v1/");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }

    @Test
    void urlWithDifferentPathIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("https://openrouter.ai/api/v2");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }

    @Test
    void httpUrlIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("http://openrouter.ai/api/v1");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }

    @Test
    void urlWithExtraWhitespaceIsTrimmedAndAccepted() {
        when(openRouterConfig.getBaseUrl()).thenReturn("  https://openrouter.ai/api/v1  ");

        assertThatCode(() -> openRouterService.validateConfiguration())
                .doesNotThrowAnyException();
    }

    @Test
    void subdomainVariationIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("https://api.openrouter.ai/api/v1");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }

    @Test
    void urlWithQueryParametersIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("https://openrouter.ai/api/v1?key=value");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }

    @Test
    void urlWithFragmentIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("https://openrouter.ai/api/v1#section");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }

    @Test
    void ssrfAttemptWithAtSymbolIsRejected() {
        when(openRouterConfig.getBaseUrl()).thenReturn("https://openrouter.ai@evil.com/api/v1");

        assertThatThrownBy(() -> openRouterService.validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid OpenRouter base URL");
    }
}
