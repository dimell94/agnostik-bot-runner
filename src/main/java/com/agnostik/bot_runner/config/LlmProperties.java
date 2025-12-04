package com.agnostik.bot_runner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String url;
    private String apiKey;
    private String apiKeyEnv;
    private String model;
    private Integer maxTokens;
    private Double temperature;
    private Integer timeoutMs;
    private Boolean enabled = true;
    private Long minIntervalMs = 8000L;

}
