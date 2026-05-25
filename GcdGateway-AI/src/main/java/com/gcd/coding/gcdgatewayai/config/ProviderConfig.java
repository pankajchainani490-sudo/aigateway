package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    private List<String> apiKeys;

    private Map<String, AtomicInteger> apiKeyQuotas = new ConcurrentHashMap<>();

    public int getRemainingQuota(String apiKey) {
        AtomicInteger quota = apiKeyQuotas.get(apiKey);
        return quota != null ? quota.get() : 0;
    }

    public void decrementQuota(String apiKey, int tokens) {
        AtomicInteger quota = apiKeyQuotas.computeIfAbsent(apiKey, k -> new AtomicInteger(Integer.MAX_VALUE));
        quota.addAndGet(-tokens);
    }

    public void incrementQuota(String apiKey, int tokens) {
        AtomicInteger quota = apiKeyQuotas.computeIfAbsent(apiKey, k -> new AtomicInteger(Integer.MAX_VALUE));
        quota.addAndGet(tokens);
    }

    public String selectApiKeyByQuota() {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return apiKey;
        }

        int totalQuota = apiKeyQuotas.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

        if (totalQuota <= 0) {
            return apiKeys.get(0);
        }

        int randomQuota = (int) (Math.random() * totalQuota);
        int cumulativeQuota = 0;

        for (String key : apiKeys) {
            AtomicInteger quota = apiKeyQuotas.get(key);
            int keyQuota = quota != null ? quota.get() : 0;
            cumulativeQuota += keyQuota;
            if (cumulativeQuota >= randomQuota) {
                return key;
            }
        }

        return apiKeys.get(0);
    }

}
