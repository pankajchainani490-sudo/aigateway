package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;

/**
 * 缓存配置 - 控制AI语义缓存的行为
 *
 * 功能说明：
 * 配置AI网关的三级语义缓存系统，包括缓存模式、TTL、容量、相似度阈值等。
 *
 * 缓存模式说明：
 * - exact：精确匹配模式，只使用L1精确匹配缓存
 * - embedding：嵌入相似度模式，使用L3嵌入相似度缓存
 * - hnsw：HNSW向量模式，使用L2 HNSW向量缓存
 * - 三级缓存：默认模式，按L1→L2→L3顺序查询
 *
 * TTL说明：
 * - 缓存数据的过期时间，默认为3600秒（1小时）
 * - 过期后自动从缓存中移除，释放内存空间
 *
 * 容量说明：
 * - 缓存的最大条目数，默认为10000
 * - 超过容量后，LRU淘汰最久未使用的条目
 *
 * 相似度阈值说明：
 * - 只有余弦相似度>=此阈值时才视为命中
 * - 默认0.95，即要求非常高的语义相似度
 *
 * @see AICacheManager 三级缓存管理器
 * @see ExactMatchCacheProvider L1精确匹配缓存
 * @see HNSWCacheProvider L2 HNSW向量缓存
 * @see EmbeddingMatchCacheProvider L3嵌入相似度缓存
 */
@Data
public class CacheConfig {

    /** 缓存功能开关，默认为true（启用） */
    private boolean enabled = true;

    /** 缓存模式：exact/embedding/hnsw，默认为"exact" */
    private String mode = "exact";

    /** 缓存过期时间（秒），默认为3600秒（1小时） */
    private int ttlSeconds = 3600;

    /** 缓存最大容量（条目数），默认为10000 */
    private int maxSize = 10000;

    /** 语义相似度阈值，默认为0.95 */
    private double similarityThreshold = 0.95;

}