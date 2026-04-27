package com.gcd.coding.gcdgatewayai.cache;

import com.gcd.coding.gcdgatewayai.config.CacheConfig;
import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.model.ChatMessage;
import com.gcd.coding.gcdgatewayai.token.TokenCounter;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class EmbeddingMatchCacheProvider implements AICacheProvider {

    private final com.github.benmanes.caffeine.cache.Cache<String, AIResponse> responseCache;

    private final Map<String, double[]> embeddingCache = new ConcurrentHashMap<>();

    private final CacheConfig config;

    public EmbeddingMatchCacheProvider(CacheConfig config) {
        this.config = config;
        this.responseCache = Caffeine.newBuilder()
                .maximumSize(config.getMaxSize())
                .expireAfterWrite(config.getTtlSeconds(), TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    @Override
    public String cacheType() {
        return "embedding";
    }

    @Override
    public AIResponse get(String cacheKey) {
        return responseCache.getIfPresent(cacheKey);
    }

    @Override
    public void put(String cacheKey, AIResponse response, long ttlMs) {
        responseCache.put(cacheKey, response);
        double[] embedding = generateSimpleEmbedding(cacheKey);
        embeddingCache.put(cacheKey, embedding);
        log.debug("嵌入相似度缓存写入: {}", cacheKey.substring(0, Math.min(16, cacheKey.length())));
    }

    @Override
    public boolean contains(String cacheKey) {
        return responseCache.getIfPresent(cacheKey) != null;
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

    public String findSimilar(String queryKey) {
        double[] queryEmbedding = generateSimpleEmbedding(queryKey);
        String bestMatch = null;
        double bestSimilarity = 0;

        for (Map.Entry<String, double[]> entry : embeddingCache.entrySet()) {
            double similarity = cosineSimilarity(queryEmbedding, entry.getValue());
            if (similarity > bestSimilarity && similarity >= config.getSimilarityThreshold()) {
                bestSimilarity = similarity;
                bestMatch = entry.getKey();
            }
        }
        if (bestMatch != null) {
            log.debug("嵌入相似度缓存命中，相似度: {:.3f}, key: {}", bestSimilarity,
                    bestMatch.substring(0, Math.min(16, bestMatch.length())));
        }
        return bestMatch;
    }

    public String findSimilarWithDetails(String queryKey) {
        return findSimilar(queryKey);
    }

    private double[] generateSimpleEmbedding(String text) {
        int dim = 128;
        double[] embedding = new double[dim];
        long hash = text.hashCode();
        Random random = new Random(hash);
        for (int i = 0; i < dim; i++) {
            embedding[i] = random.nextGaussian();
        }
        double norm = 0;
        for (double v : embedding) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) embedding[i] /= norm;
        }
        return embedding;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    public long size() {
        return responseCache.estimatedSize();
    }

    @Override
    public void clear() {
        responseCache.invalidateAll();
        embeddingCache.clear();
    }

}
