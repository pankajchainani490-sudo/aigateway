package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;

@Data
public class ProviderConfig {

    private String name;

    private String type = "openai";

    private String baseUrl;

    private String apiKey;

    private String protocol = "openai";

    private boolean supportsStreaming = true;

    private int connectTimeout = 30000;

    private int requestTimeout = 120000;

    private int maxRetries = 3;

    private int maxConnections = 50;

}
