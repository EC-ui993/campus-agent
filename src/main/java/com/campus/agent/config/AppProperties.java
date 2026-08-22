package com.campus.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String apiKey, String model, String baseUrl, String dbPath, String accessToken
) {}
