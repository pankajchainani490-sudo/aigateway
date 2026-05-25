package com.gcd.coding.gcdgatewayai.cache;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;

/**
 * AI缓存提供者接口 - 定义缓存操作的标准接口
 *
 * 功能说明：
 * 定义AI语义缓存的基本操作，所有缓存实现（如精确匹配、HNSW向量、嵌入相似度）
 * 都必须实现此接口。这是三级缓存体系的基础接口。
 *
 * 实现类：
 * - ExactMatchCacheProvider：L1精确匹配缓存（SHA-256哈希）
 * - HNSWCacheProvider：L2 HNSW向量缓存
 * - EmbeddingMatchCacheProvider：L3嵌入相似度缓存
 *
 * 三级缓存查询流程：
 * 1. AISemanticCacheFilter调用lookupFromThreeLevelCache()
 * 2. 按L1→L2→L3顺序查询各级缓存
 * 3. 任意层级命中则返回缓存响应
 * 4. 全部未命中则回源到AI服务
 * 5. AI服务返回后，写入三级缓存
 *
 * @see AICacheManager 三级缓存管理器
 */
public interface AICacheProvider {

    /**
     * 返回缓存类型标识
     *
     * 用于在AICacheManager中标识和区分不同的缓存实现
     *
     * @return 缓存类型标识，如 "exact"、"hnsw"、"embedding"
     */
    String cacheType();

    /**
     * 根据缓存Key获取AI响应
     *
     * @param cacheKey 缓存Key（通常为SHA-256哈希的十六进制字符串）
     * @return 缓存的AI响应对象，若未命中返回null
     */
    AIResponse get(String cacheKey);

    /**
     * 写入缓存
     *
     * @param cacheKey 缓存Key
     * @param response 要缓存的AI响应对象
     * @param ttlMs 过期时间（毫秒）
     */
    void put(String cacheKey, AIResponse response, long ttlMs);

    /**
     * 检查缓存Key是否存在
     *
     * @param cacheKey 缓存Key
     * @return true表示存在，false表示不存在
     */
    boolean contains(String cacheKey);

    /**
     * 生成AI请求对应的缓存Key
     *
     * 生成算法因缓存类型而异：
     * - 精确匹配：SHA-256哈希
     * - HNSW/嵌入：生成向量后存储
     *
     * @param request AI请求对象
     * @return 缓存Key
     */
    String generateKey(AIRequest request);

    /**
     * 获取当前缓存的条目数
     *
     * @return 近似缓存大小
     */
    long size();

    /**
     * 清空所有缓存
     */
    void clear();

}