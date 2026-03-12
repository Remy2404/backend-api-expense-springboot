package com.wing.backendapiexpensespringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "imagekit")
public class ImageKitProperties {

    private boolean enabled = false;
    private String publicKey;
    private String privateKey;
    private String urlEndpoint;
    private String receiptFolder = "/receipts";
    private long maxFileSizeBytes = 10L * 1024L * 1024L;
    private List<String> allowedMimeTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic");
    private long uploadTokenTtlSeconds = 900;
    private long signedUrlTtlSeconds = 900;
    private boolean privateFile = true;
    private boolean useUniqueFileName = true;
}
