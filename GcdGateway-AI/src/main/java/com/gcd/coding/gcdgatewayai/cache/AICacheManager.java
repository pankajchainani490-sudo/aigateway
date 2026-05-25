package com.gcd.coding.gcdgatewayai.cache;

// ========== 导入部分 ==========

// 缓存配置类 - 包含TTL、最大容量、相似度阈值等
import com.gcd.coding.gcdgatewayai.config.CacheConfig;

// AI请求模型 - 用户发送的AI请求
import com.gcd.coding.gcdgatewayai.model.AIRequest;

// AI响应模型 - AI服务返回的响应
import com.gcd.coding.gcdgatewayai.model.AIResponse;

// Lombok日志注解
import lombok.extern.slf4j.Slf4j;

// Java并发工具类
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI语义缓存管理器
 *
 * 功能说明：
 * 管理AI网关的三级语义缓存系统，协调多个缓存提供者（Provider）工作。
 * 实现缓存查询的级联命中和统一写入。
 *
 * 三级缓存架构：
 * ┌─────────────────────────────────────────────────────────────┐
 * │                      L1 精确匹配缓存                         │
 * │  ExactMatchCacheProvider - SHA-256精确哈希匹配              │
 * │  特点：100%准确，命中即返回，O(1)时间复杂度                  │
 * └─────────────────────────────────────────────────────────────┘
 *                              ↓ 未命中
 * ┌─────────────────────────────────────────────────────────────┐
 * │                      L2 HNSW向量缓存                        │
 * │  HNSWCacheProvider - HNSW向量索引，余弦相似度≥0.95         │
 * │  特点：语义相似度搜索，O(log n)时间复杂度                   │
 * │  注意：hnswlib-java库不可用，当前为内存自实现               │
 * └─────────────────────────────────────────────────────────────┘
 *                              ↓ 未命中
 * ┌─────────────────────────────────────────────────────────────┐
 * │                      L3 嵌入相似度缓存                       │
 * │  EmbeddingMatchCacheProvider - 简单嵌入向量相似度           │
 * │  特点：后备方案，穷举式相似度计算                          │
 * └─────────────────────────────────────────────────────────────┘
 *                              ↓ 未命中
 * ┌─────────────────────────────────────────────────────────────┐
 * │                      回源到AI Provider                      │
 * │  调用真实的AI服务（如OpenAI、Claude等）                     │
 * └─────────────────────────────────────────────────────────────┘
 *
 * 缓存命中流程：
 * 1. AISemanticCacheFilter收到AI请求
 * 2. 生成请求的缓存Key（SHA-256哈希）
 * 3. 按L1→L2→L3顺序查询缓存
 * 4. 任意层级命中则返回缓存响应
 * 5. 全部未命中则回源到AI服务
 * 6. AI服务返回后，写入三级缓存
 *
 * 使用场景：
 * - 用户发送重复或相似的AI请求时，直接返回缓存响应
 * - 降低AI服务调用次数，节省成本
 * - 提升响应速度，改善用户体验
 *
 * 线程安全：
 * - 使用ConcurrentHashMap存储Provider
 * - 单例模式保证全局唯一实例
 * - 各Provider内部自己管理线程安全
 */
@Slf4j
public class AICacheManager {

    // ========== 单例相关 ==========

    /**
     * 单例实例
     *
     * 使用延迟初始化模式，在第一次调用getInstance()时创建
     * 构造函数私有化，防止外部直接创建实例
     */
    private static final AICacheManager INSTANCE = new AICacheManager();

    // ========== 缓存Provider管理 ==========

    /**
     * 缓存Provider映射表
     *
     * Key: Provider类型标识（"exact", "hnsw", "embedding"）
     * Value: 缓存Provider实例
     *
     * 使用ConcurrentHashMap保证线程安全
     * 支持运行时动态添加新的Provider实现
     */
    private final Map<String, AICacheProvider> cacheProviderMap = new ConcurrentHashMap<>();

    /**
     * 当前默认使用的缓存Provider
     *
     * 用途：
     * - 用于单级缓存模式（如配置mode=exact时只使用精确匹配）
     * - 可通过switchMode()动态切换
     *
     * 注意：三级缓存模式不使用此字段，而是调用lookupFromThreeLevelCache()
     */
    private AICacheProvider defaultCacheProvider;

    /**
     * 缓存配置引用
     *
     * 保存当前缓存配置，用于：
     * - 获取TTL（过期时间）
     * - 获取最大容量
     * - 获取相似度阈值
     */
    private CacheConfig cacheConfig;

    // ========== 三级缓存Provider实例 ==========

    /**
     * L1: 精确匹配缓存Provider
     *
     * 原理：使用SHA-256哈希精确匹配
     * 适用场景：完全相同的请求（相同模型+相同消息）
     * 特点：100%准确，无误判
     */
    private ExactMatchCacheProvider exactProvider;

    /**
     * L2: HNSW向量缓存Provider
     *
     * 原理：HNSW图索引 + 余弦相似度搜索
     * 适用场景：语义相似的请求（不同措辞表达相同意思）
     * 特点：支持近似匹配，相似度阈值默认0.95
     * 注意：因hnswlib-java库不可用，使用内存自实现
     */
    private HNSWCacheProvider hnswProvider;

    /**
     * L3: 嵌入相似度缓存Provider
     *
     * 原理：简单向量嵌入 + 穷举式相似度计算
     * 适用场景：作为HNSW的备用方案
     * 特点：实现简单，但查询时间复杂度O(n)
     */
    private EmbeddingMatchCacheProvider embeddingProvider;

    // ========== 构造函数 ==========

    /**
     * 私有构造函数
     *
     * 外部无法直接创建实例，必须通过getInstance()获取
     */
    private AICacheManager() {
        // 私有构造函数
    }

    // ========== 单例访问方法 ==========

    /**
     * 获取AICacheManager单例实例
     *
     * @return AICacheManager全局唯一实例
     */
    public static AICacheManager getInstance() {
        return INSTANCE;
    }

    // ========== 初始化方法 ==========

    /**
     * 初始化AI缓存系统
     *
     * 调用时机：
     * - AI网关启动时
     * - AI模块配置变更时
     *
     * 初始化步骤：
     * 1. 保存缓存配置
     * 2. 创建L1精确匹配Provider
     * 3. 创建L2 HNSW向量Provider
     * 4. 创建L3嵌入相似度Provider
     * 5. 设置默认Provider
     *
     * @param config 缓存配置对象（包含TTL、容量、模式等）
     */
    public void init(CacheConfig config) {
        this.cacheConfig = config;

        // 第1步：创建L1精确匹配Provider
        exactProvider = new ExactMatchCacheProvider(config);
        cacheProviderMap.put(exactProvider.cacheType(), exactProvider);

        // 第2步：创建L2 HNSW向量Provider
        hnswProvider = new HNSWCacheProvider(config);
        cacheProviderMap.put(hnswProvider.cacheType(), hnswProvider);

        // 第3步：创建L3嵌入相似度Provider
        embeddingProvider = new EmbeddingMatchCacheProvider(config);
        cacheProviderMap.put(embeddingProvider.cacheType(), embeddingProvider);

        // 第4步：根据配置设置默认Provider
        this.defaultCacheProvider = resolveProvider(config.getMode());

        log.info("AI语义缓存初始化完成，模式: {}, 三级缓存就绪 (L1:精确匹配, L2:HNSW向量, L3:嵌入相似度)", config.getMode());
    }

    // ========== Provider获取方法 ==========

    /**
     * 获取当前默认的缓存Provider
     *
     * 用途：
     * - 单级缓存模式下的缓存查询
     * - 与lookupFromThreeLevelCache()区分使用
     *
     * @return 默认缓存Provider实例
     */
    public AICacheProvider getCurrentProvider() {
        return defaultCacheProvider;
    }

    /**
     * 根据类型获取缓存Provider
     *
     * @param type Provider类型标识（"exact", "hnsw", "embedding"）
     * @return 对应类型的Provider，若不存在返回null
     */
    public AICacheProvider getProvider(String type) {
        return cacheProviderMap.get(type);
    }

    /**
     * 获取L3嵌入相似度缓存Provider
     *
     * @return EmbeddingMatchCacheProvider实例
     */
    public EmbeddingMatchCacheProvider getEmbeddingProvider() {
        return embeddingProvider;
    }

    /**
     * 获取L2 HNSW向量缓存Provider
     *
     * @return HNSWCacheProvider实例
     */
    public HNSWCacheProvider getHnswProvider() {
        return hnswProvider;
    }

    /**
     * 获取L1精确匹配缓存Provider
     *
     * @return ExactMatchCacheProvider实例
     */
    public ExactMatchCacheProvider getExactProvider() {
        return exactProvider;
    }

    // ========== 缓存模式切换 ==========

    /**
     * 切换缓存模式
     *
     * 用途：
     * - 动态调整缓存策略
     * - 运行时切换精确匹配/语义匹配模式
     *
     * @param newMode 新缓存模式（"exact", "hnsw", "embedding"）
     */
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

    /**
     * 获取缓存配置
     *
     * @return 缓存配置对象
     */
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    /**
     * 根据模式解析对应的Provider
     *
     * @param mode 缓存模式
     * @return 对应模式的Provider实例
     */
    private AICacheProvider resolveProvider(String mode) {
        if ("exact".equalsIgnoreCase(mode)) {
            return cacheProviderMap.get("exact");
        } else if ("embedding".equalsIgnoreCase(mode)) {
            return cacheProviderMap.get("embedding");
        } else if ("hnsw".equalsIgnoreCase(mode)) {
            return cacheProviderMap.get("hnsw");
        }
        // 默认返回精确匹配Provider
        return cacheProviderMap.get("exact");
    }

    // ========== 三级缓存核心方法 ==========

    /**
     * 三级缓存查询
     *
     * 查询流程（按优先级）：
     *
     * L1精确匹配（最高优先级）：
     * - 使用SHA-256生成请求哈希作为缓存Key
     * - 直接从Caffeine缓存中查找
     * - 命中返回，相似度=1.0
     *
     * L2 HNSW向量（次优先级）：
     * - L1未命中时触发
     * - 生成请求的向量表示
     * - 在HNSW图中搜索最相似的缓存Key
     * - 余弦相似度≥0.95时视为命中
     *
     * L3嵌入相似度（最低优先级）：
     * - L2未命中或HNSW不可用时触发
     * - 穷举遍历所有缓存向量
     * - 计算余弦相似度，取最高者
     * - 相似度≥0.95时视为命中
     *
     * @param request AI请求对象
     * @return 缓存查询结果（包含响应、缓存模式命中的缓存Key）
     *
     * 使用示例：
     * CacheLookupResult result = AICacheManager.getInstance().lookupFromThreeLevelCache(request);
     * if (result.isHit()) {
     *     // 命中缓存，使用result.response()
     * } else {
     *     // 未命中，回源到AI服务
     * }
     */
    public CacheLookupResult lookupFromThreeLevelCache(AIRequest request) {
        // 第1步：生成请求的缓存Key
        String cacheKey = exactProvider.generateKey(request);

        // 第2步：L1精确匹配查询
        AIResponse cachedResponse = exactProvider.get(cacheKey);
        if (cachedResponse != null) {
            log.debug("L1 精确匹配缓存命中");
            return new CacheLookupResult(cachedResponse, "exact", cacheKey);
        }

        // 第3步：L2 HNSW向量查询
        if (hnswProvider != null) {
            String hnswKey = hnswProvider.findSimilar(cacheKey);
            if (hnswKey != null) {
                AIResponse hnswResponse = hnswProvider.get(hnswKey);
                if (hnswResponse != null) {
                    log.debug("L2 HNSW向量缓存命中");
                    return new CacheLookupResult(hnswResponse, "hnsw", hnswKey);
                }
            }
        }

        // 第4步：L3嵌入相似度查询
        if (embeddingProvider != null) {
            String embeddingKey = embeddingProvider.findSimilar(cacheKey);
            if (embeddingKey != null) {
                AIResponse embeddingResponse = embeddingProvider.get(embeddingKey);
                if (embeddingResponse != null) {
                    log.debug("L3 嵌入相似度缓存命中");
                    return new CacheLookupResult(embeddingResponse, "embedding", embeddingKey);
                }
            }
        }

        // 第5步：全部未命中，返回null
        return new CacheLookupResult(null, null, cacheKey);
    }

    /**
     * 写入三级缓存
     *
     * 写入策略：
     * - 同时写入L1、L2、L3三级缓存
     * - 使用统一的TTL（从配置中获取）
     * - 写入失败不影响其他级别
     *
     * 注意：
     * - 同一请求的L1、L2、L3缓存Key相同（都是SHA-256哈希）
     * - 但存储的向量不同（L1不存向量，L2存HNSW向量，L3存简单嵌入向量）
     *
     * @param request AI请求对象（用于生成缓存Key）
     * @param response AI响应对象（要缓存的内容）
     */
    public void putToAllLevelCaches(AIRequest request, AIResponse response) {
        // 第1步：生成缓存Key
        String cacheKey = exactProvider.generateKey(request);
        // 转换TTL为毫秒
        long ttlMs = cacheConfig.getTtlSeconds() * 1000L;

        // 第2步：写入L1精确匹配缓存
        exactProvider.put(cacheKey, response, ttlMs);

        // 第3步：写入L2 HNSW向量缓存
        if (hnswProvider != null) {
            hnswProvider.put(cacheKey, response, ttlMs);
        }

        // 第4步：写入L3嵌入相似度缓存
        if (embeddingProvider != null) {
            embeddingProvider.put(cacheKey, response, ttlMs);
        }
    }

    // ========== 内部类 ==========

    /**
     * 缓存查询结果
     *
     * 使用Java record实现简洁的不可变结果类
     *
     * @param response 缓存的AI响应（若未命中则为null）
     * @param cacheMode 命中的缓存模式（"exact", "hnsw", "embedding", null）
     * @param cacheKey 使用的缓存Key
     */
    public record CacheLookupResult(AIResponse response, String cacheMode, String cacheKey) {
        /**
         * 判断是否命中缓存
         *
         * @return true表示命中，false表示未命中
         */
        public boolean isHit() {
            return response != null;
        }
    }
}
