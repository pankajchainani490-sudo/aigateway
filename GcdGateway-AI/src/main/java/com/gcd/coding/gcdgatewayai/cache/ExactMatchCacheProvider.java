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

/**
 * 精确匹配缓存提供者 - L1级语义缓存
 *
 * 功能说明：
 * 实现基于SHA-256哈希的精确匹配缓存，对完全相同的请求（相同模型+相同消息）
 * 进行缓存命中判断。这是三级缓存体系中的第一级（L1），拥有最高的命中精度。
 *
 * 工作原理：
 * 1. 缓存Key生成：对请求的模型名和所有消息内容进行SHA-256哈希，生成64位十六进制字符串
 * 2. 缓存查询：使用Caffeine本地缓存存储AIResponse，O(1)时间复杂度查找
 * 3. 缓存写入：将AI响应写入Caffeine缓存，设置过期时间和最大容量
 *
 * 缓存特点：
 * - 精确性：100%准确，只有请求完全相同才命中
 * - 高效性：SHA-256哈希 + Caffeine缓存，查找速度极快
 * - 简单性：无需复杂的向量计算或相似度比较
 *
 * 与其他缓存的关系：
 * - L1（精确匹配）：SHA-256精确哈希，最快最准
 * - L2（HNSW向量）：语义相似度搜索，适用于措辞不同但含义相同
 * - L3（嵌入相似度）：简单向量相似度，作为L2的备用
 *
 * @see AICacheManager 三级缓存管理器
 * @see HNSWCacheProvider L2级HNSW向量缓存
 * @see EmbeddingMatchCacheProvider L3级嵌入相似度缓存
 */
@Slf4j
public class ExactMatchCacheProvider implements AICacheProvider {

    /** Caffeine本地缓存实例，存储缓存Key到AIResponse的映射 */
    private final com.github.benmanes.caffeine.cache.Cache<String, AIResponse> cache;

    /** 缓存配置引用，包含TTL、最大容量等 */
    private final CacheConfig config;

    /** JSON序列化工具，用于将来可能的序列化需求 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数 - 创建精确匹配缓存提供者
     *
     * 初始化步骤：
     * 1. 保存缓存配置引用
     * 2. 创建Caffeine缓存实例，配置最大容量和过期时间
     * 3. 开启缓存统计（recordStats），用于监控命中率
     *
     * @param config 缓存配置对象（包含TTL、容量等）
     */
    public ExactMatchCacheProvider(CacheConfig config) {
        this.config = config;
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.getMaxSize())  // 最大缓存条目数
                .expireAfterWrite(config.getTtlSeconds(), TimeUnit.SECONDS)  // 写入后TTL过期
                .recordStats()  // 开启统计，用于监控
                .build();
    }

    /**
     * 返回缓存类型标识
     *
     * @return "exact" - 用于在AICacheManager中标识此缓存类型
     */
    @Override
    public String cacheType() {
        return "exact";
    }

    /**
     * 根据缓存Key获取AI响应
     *
     * @param cacheKey 缓存Key（SHA-256哈希的十六进制字符串）
     * @return 缓存的AI响应对象，若未命中返回null
     */
    @Override
    public AIResponse get(String cacheKey) {
        AIResponse response = cache.getIfPresent(cacheKey);
        if (response != null) {
            log.debug("精确匹配缓存命中: {}", cacheKey.substring(0, Math.min(16, cacheKey.length())));
        }
        return response;
    }

    /**
     * 写入缓存
     *
     * @param cacheKey 缓存Key
     * @param response 要缓存的AI响应对象
     * @param ttlMs 过期时间（毫秒），本实现使用config中的TTL
     */
    @Override
    public void put(String cacheKey, AIResponse response, long ttlMs) {
        cache.put(cacheKey, response);
        log.debug("精确匹配缓存写入: {}", cacheKey.substring(0, Math.min(16, cacheKey.length())));
    }

    /**
     * 检查缓存Key是否存在
     *
     * @param cacheKey 缓存Key
     * @return true表示存在，false表示不存在
     */
    @Override
    public boolean contains(String cacheKey) {
        return cache.getIfPresent(cacheKey) != null;
    }

    /**
     * 生成AI请求对应的缓存Key
     *
     * 生成算法：
     * 1. 规范化请求文本：模型名 + 所有消息的角色:内容拼接
     *    格式：模型名|角色1:内容1|角色2:内容2|...
     * 2. 对规范化文本计算SHA-256哈希
     * 3. 返回十六进制哈希字符串（64位）作为缓存Key
     *
     * 相同请求（相同模型+相同消息）会产生相同的Key，实现精确匹配
     *
     * @param request AI请求对象
     * @return SHA-256哈希后的缓存Key（64位十六进制字符串）
     */
    @Override
    public String generateKey(AIRequest request) {
        try {
            // 第1步：规范化请求文本
            // 格式：模型名|角色1:内容1|角色2:内容2|...
            String normalized = request.getModel() + "|" +
                    (request.getMessages() != null
                            ? request.getMessages().stream()
                            .map(m -> m.getRole() + ":" + m.getContent())
                            .collect(Collectors.joining("|"))
                            : "");

            // 第2步：计算SHA-256哈希
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));

            // 第3步：转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // SHA-256算法不会抛异常，捕获是为了防御性编程
            return String.valueOf(request.hashCode());
        }
    }

    /**
     * 获取当前缓存的条目数
     *
     * @return 近似缓存大小
     */
    @Override
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 清空所有缓存
     */
    @Override
    public void clear() {
        cache.invalidateAll();
    }

}