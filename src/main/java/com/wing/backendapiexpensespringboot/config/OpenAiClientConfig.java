package com.wing.backendapiexpensespringboot.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class OpenAiClientConfig {

    @Bean
    public OpenAIClient openAIClient(OpenRouterConfig config) {
        log.info("Initializing OpenAI SDK client with base URL: {}", config.getBaseUrl());
        return OpenAIOkHttpClient.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .build();
    }
}
