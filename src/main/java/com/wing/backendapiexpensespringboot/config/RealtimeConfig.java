package com.wing.backendapiexpensespringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "realtime")
public class RealtimeConfig {

    private String relayUrl;
    private String relaySecret;
    private String publicSocketUrl;
    private long tokenTtlSeconds = 3600;
}
