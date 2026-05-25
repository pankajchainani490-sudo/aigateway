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

/**
 * 嵌入相似度缓存提供者 - L3级语义缓存（后备方案）
 *
 * 功能说明：
 * 实现基于简单向量嵌入的语义相似度缓存。在L1（精确匹配）和L2（HNSW向量）
 * 都未命中时，作为最后的后备方案，通过穷举式向量相似度计算找到语义相似的缓存。
 *
 * 工作原理：
 * 1. 缓存Key生成：与精确匹配相同，使用SHA-256哈希
 * 2. 向量生成：为每个缓存Key生成128维归一化向量（基于文本哈希的高斯随机向量）
 * 3. 相似度计算：计算查询向量与所有缓存向量的余弦相似度，返回最高者
 *
 * 与L2（HNSW）的区别：
 * - L2：使用HNSW图索引进行O(log n)近似最近邻搜索，适合大规模缓存
 * - L3：穷举遍历所有向量进行O(n)相似度计算，作为L2不可用时的备用
 *
 * 相似度阈值：默认0.95，即余弦相似度>=0.95时认为语义相似
 *
 * @see AICacheManager 三级缓存管理器
 * @see HNSWCacheProvider L2级HNSW向量缓存
 * @see ExactMatchCacheProvider L1级精确匹配缓存
 */
@Slf4j
public class EmbeddingMatchCacheProvider implements AICacheProvider {

    /** 响应缓存（Caffeine），存储缓存Key到AIResponse的映射 */
    private final com.github.benmanes.caffeine.cache.Cache<String, AIResponse> responseCache;

    /** 向量缓存，存储缓存Key到128维向量的映射 */
    private final Map<String, double[]> embeddingCache = new ConcurrentHashMap<>();

    /** 缓存配置引用 */
    private final CacheConfig config;

    /**
     * 构造函数 - 创建嵌入相似度缓存提供者
     *
     * @param config 缓存配置对象
     */
    public EmbeddingMatchCacheProvider(CacheConfig config) {
        this.config = config;
        this.responseCache = Caffeine.newBuilder()
                .maximumSize(config.getMaxSize())  // 最大缓存条目数
                .expireAfterWrite(config.getTtlSeconds(), TimeUnit.SECONDS)  // 写入后TTL过期
                .recordStats()  // 开启统计
                .build();
    }

    /**
     * 返回缓存类型标识
     *
     * @return "embedding"
     */
    @Override
    public String cacheType() {
        return "embedding";
    }

    /**
     * 根据缓存Key获取AI响应
     *
     * @param cacheKey 缓存Key
     * @return 缓存的AI响应对象，若未命中返回null
     */
    @Override
    public AIResponse get(String cacheKey) {
        return responseCache.getIfPresent(cacheKey);
    }

    /**
     * 写入缓存
     *
     * 执行步骤：
     * 1. 将响应写入responseCache
     * 2. 生成该缓存Key对应的向量
     * 3. 将向量存入embeddingCache
     *
     * @param cacheKey 缓存Key
     * @param response 要缓存的AI响应对象
     * @param ttlMs 过期时间（毫秒）
     */
    @Override
    public void put(String cacheKey, AIResponse response, long ttlMs) {
        responseCache.put(cacheKey, response);
        double[] embedding = generateSimpleEmbedding(cacheKey);
        embeddingCache.put(cacheKey, embedding);
        log.debug("嵌入相似度缓存写入: {}", cacheKey.substring(0, Math.min(16, cacheKey.length())));
    }

    /**
     * 检查缓存Key是否存在
     *
     * @param cacheKey 缓存Key
     * @return true表示存在，false表示不存在
     */
    @Override
    public boolean contains(String cacheKey) {
        return responseCache.getIfPresent(cacheKey) != null;
    }

    /**
     * 生成AI请求对应的缓存Key
     *
     * 与精确匹配缓存相同的算法：SHA-256哈希
     *
     * @param request AI请求对象
     * @return SHA-256哈希后的缓存Key
     */
    @Override
    public String generateKey(AIRequest request) {
        try {
            // 规范化请求文本：模型名 + 所有消息的角色:内容拼接
            String normalized = request.getModel() + "|" +
                    (request.getMessages() != null
                            ? request.getMessages().stream()
                            .map(m -> m.getRole() + ":" + m.getContent())
                            .collect(Collectors.joining("|"))
                            : "");

            // 计算SHA-256哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
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

    /**
     * 查找与给定Key最相似的缓存Key
     *
     * 算法：穷举式遍历所有缓存向量，计算余弦相似度，返回最高的
     *
     * @param queryKey 查询Key
     * @return 最相似的缓存Key，若无相似度>=阈值则返回null
     */
    public String findSimilar(String queryKey) {
        double[] queryEmbedding = generateSimpleEmbedding(queryKey);
        String bestMatch = null;
        double bestSimilarity = 0;

        // 遍历所有缓存向量，找最相似的
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

    /**
     * 查找相似Key（带详情）
     *
     * @param queryKey 查询Key
     * @return 相似Key或null
     */
    public String findSimilarWithDetails(String queryKey) {
        return findSimilar(queryKey);
    }

    /**
     * 从文本生成128维归一化向量
     *
     * 算法：
     * 1. 使用文本的哈希值作为随机种子，保证相同文本产生相同向量
     * 2. 使用高斯分布（均值0，标准差1）生成128维向量
     * 3. L2归一化，使向量长度为1
     *
     * 注意：这是简化实现，实际应使用真实的嵌入模型
     *
     * @param text 输入文本
     * @return 128维归一化向量
     */
    private double[] generateSimpleEmbedding(String text) {
        int dim = 128;
        double[] embedding = new double[dim];
        long hash = text.hashCode();
        Random random = new Random(hash);
        for (int i = 0; i < dim; i++) {
            embedding[i] = random.nextGaussian();
        }
        // L2归一化
        double norm = 0;
        for (double v : embedding) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) embedding[i] /= norm;
        }
        return embedding;
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * 公式：cosine_similarity(A,B) = (A·B) / (||A|| × ||B||)
     * 由于向量已归一化，简化为：A·B
     *
     * @param a 向量A
     * @param b 向量B
     * @return 余弦相似度（-1到1）
     */
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