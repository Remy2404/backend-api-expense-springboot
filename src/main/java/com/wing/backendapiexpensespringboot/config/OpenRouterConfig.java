package com.wing.backendapiexpensespringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Data
@Configuration
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterConfig {

    private String apiKey;
    private String model;
    private String baseUrl;
    private int timeout;

    @Bean
    public RestTemplate openRouterRestTemplate() {
        return new RestTemplate();
    }
}
