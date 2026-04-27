package com.gcd.coding.gcdgatewayai.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gcd.coding.gcdgatewayai.config.CacheConfig;
import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.model.ChatMessage;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ExactMatchCacheProvider implements AICacheProvider {

    private final com.github.benmanes.caffeine.cache.Cache<String, AIResponse> cache;

    private final CacheConfig config;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExactMatchCacheProvider(CacheConfig config) {
        this.config = config;
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.getMaxSize())
                .expireAfterWrite(config.getTtlSeconds(), TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    @Override
    public String cacheType() {
        return "exact";
    }

    @Override
    public AIResponse get(String cacheKey) {
        AIResponse response = cache.getIfPresent(cacheKey);
        if (response != null) {
            log.debug("精确匹配缓存命中: {}", cacheKey.substring(0, Math.min(16, cacheKey.length())));
        }
        return response;
    }

    @Override
    public void put(String cacheKey, AIResponse response, long ttlMs) {
        cache.put(cacheKey, response);
        log.debug("精确匹配缓存写入: {}", cacheKey.substring(0, Math.min(16, cacheKey.length())));
    }

    @Override
    public boolean contains(String cacheKey) {
        return cache.getIfPresent(cacheKey) != null;
    }

    @Override
    public String generateKey(AIRequest request) {
        try {
            String normalized = request.getModel() + "|" +
                    (request.getMessages() != null
                            ? request.getMessages().stream()
                            .map(m -> m.getRole() + ":" + m.getContent())
                            .collect(Collectors.joining("|"))
                            : "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(request.hashCode());
        }
    }

    @Override
    public long size() {
        return cache.estimatedSize();
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

}
