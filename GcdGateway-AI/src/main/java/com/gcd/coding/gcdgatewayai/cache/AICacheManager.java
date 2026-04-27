package com.gcd.coding.gcdgatewayai.cache;

import com.gcd.coding.gcdgatewayai.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AICacheManager {

    private static final AICacheManager INSTANCE = new AICacheManager();

    private final Map<String, AICacheProvider> cacheProviderMap = new ConcurrentHashMap<>();

    private AICacheProvider defaultCacheProvider;

    private CacheConfig cacheConfig;

    private AICacheManager() {
    }

    public static AICacheManager getInstance() {
        return INSTANCE;
    }

    public void init(CacheConfig config) {
        this.cacheConfig = config;
        ExactMatchCacheProvider exactProvider = new ExactMatchCacheProvider(config);
        cacheProviderMap.put(exactProvider.cacheType(), exactProvider);

        EmbeddingMatchCacheProvider embeddingProvider = new EmbeddingMatchCacheProvider(config);
        cacheProviderMap.put(embeddingProvider.cacheType(), embeddingProvider);

        this.defaultCacheProvider = resolveProvider(config.getMode());
        log.info("AI语义缓存初始化完成，模式: {}, 精确匹配缓存就绪, 嵌入相似度缓存就绪", config.getMode());
    }

    public AICacheProvider getCurrentProvider() {
        return defaultCacheProvider;
    }

    public AICacheProvider getProvider(String type) {
        return cacheProviderMap.get(type);
    }

    public EmbeddingMatchCacheProvider getEmbeddingProvider() {
        return (EmbeddingMatchCacheProvider) cacheProviderMap.get("embedding");
    }

    public void switchMode(String newMode) {
        AICacheProvider provider = resolveProvider(newMode);
        if (provider != null) {
            this.defaultCacheProvider = provider;
            if (cacheConfig != null) {
                cacheConfig.setMode(newMode);
            }
            log.info("AI缓存模式切换为: {}", newMode);
        }
    }

    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    private AICacheProvider resolveProvider(String mode) {
        if ("exact".equalsIgnoreCase(mode)) {
            return cacheProviderMap.get("exact");
        } else if ("embedding".equalsIgnoreCase(mode)) {
            return cacheProviderMap.get("embedding");
        }
        return cacheProviderMap.get("exact");
    }

}
