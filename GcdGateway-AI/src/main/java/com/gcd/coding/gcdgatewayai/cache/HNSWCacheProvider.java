package com.gcd.coding.gcdgatewayai.cache;

// ========== 导入部分 ==========

// 缓存配置类 - 包含TTL、最大容量、相似度阈值等配置
import com.gcd.coding.gcdgatewayai.config.CacheConfig;

// AI请求模型 - 用户发送的请求对象
import com.gcd.coding.gcdgatewayai.model.AIRequest;

// AI响应模型 - AI服务返回的响应对象
import com.gcd.coding.gcdgatewayai.model.AIResponse;

// Caffeine缓存接口和实现 - 高性能本地缓存
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

// Lombok日志注解
import lombok.extern.slf4j.Slf4j;

// Java NIO字符编码 - 用于字符串转字节
import java.nio.charset.StandardCharsets;

// 消息摘要 - 用于SHA-256哈希计算
import java.security.MessageDigest;

// Java集合框架
import java.util.*;                      // List, Map, Set, Random等
import java.util.concurrent.ConcurrentHashMap;  // 线程安全的HashMap
import java.util.concurrent.TimeUnit;           // 时间单位转换
import java.util.stream.Collectors;            // Stream收集器

/**
 * HNSW语义缓存提供者
 *
 * 功能说明：
 * 实现基于HNSW（Hierarchical Navigable Small World）算法的向量相似度缓存。
 * 用于AI语义缓存的第二层（L2），在精确匹配（L1）未命中时，
 * 通过向量相似度查找语义相近的已缓存响应。
 *
 * HNSW算法简介：
 * - 一种基于图的近似最近邻搜索算法
 * - 构建多层跳跃搜索图，实现O(log n)时间复杂度
 * - 支持高维向量（128维）的快速相似度搜索
 *
 * 本实现为内存版本（in-memory），适用于中小规模缓存场景。
 * 大规模部署建议使用专门的向量数据库（如Milvus、Pinecone）。
 *
 * 缓存层次结构：
 * - L1（第一层）：ExactMatchCacheProvider - SHA-256精确匹配
 * - L2（第二层）：HNSWCacheProvider - HNSW向量相似度（余弦相似度>=0.95）
 * - L3（第三层）：EmbeddingMatchCacheProvider - 简单嵌入相似度（后备）
 *
 * 相似度阈值：默认0.95，即余弦相似度>=0.95时认为语义相似
 */
@Slf4j
public class HNSWCacheProvider implements AICacheProvider {

    // ========== HNSW算法常量 ==========

    /**
     * 向量维度
     *
     * 说明：每个文本被转换为128维浮点向量
     * 维度越高表达能力越强，但计算量也越大
     * 128是HNSW论文推荐的默认值
     */
    private static final int HNSW_DIMENSION = 128;

    /**
     * 每个节点的最大连接数（M）
     *
     * 说明：HNSW图中每个节点最多连接M个最近邻
     * - M值越大，搜索精度越高，但内存占用越大
     * - M=16是HNSW论文推荐的高精度配置
     */
    private static final int MAX_CONNECTIONS = 16;

    /**
     * 构建时的搜索范围（efConstruction）
     *
     * 说明：插入节点时搜索的候选邻居数量
     * - 值越大，构建越慢，但图质量越高
     * - efConstruction=200是精度和性能的平衡点
     */
    private static final int EF_CONSTRUCTION = 200;

    /**
     * 搜索时的搜索范围（efSearch）
     *
     * 说明：搜索时维护的候选队列大小
     * - 值越大，搜索精度越高，但延迟也越大
     * - efSearch=50是高搜索性能的推荐值
     */
    private static final int EF_SEARCH = 50;

    // ========== 缓存组件 ==========

    /**
     * 响应缓存（Caffeine）
     *
     * 存储结构：Key=缓存Key（SHA-256哈希），Value=AI响应对象
     * 淘汰策略：LRU（最近最少使用）
     * 过期时间：由CacheConfig.ttlSeconds配置
     * 最大容量：由CacheConfig.maxSize配置
     */
    private final Cache<String, AIResponse> responseCache;

    /**
     * 向量缓存
     *
     * 存储结构：Key=缓存Key，Value=128维浮点向量
     * 与responseCache一一对应，用于向量相似度计算
     */
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    /**
     * 缓存配置引用
     *
     * 用于获取：
     * - TTL（过期时间）
     * - 最大容量
     * - 相似度阈值
     */
    private final CacheConfig config;

    /**
     * 图结构锁
     *
     * 原因：HNSW图结构的插入操作不是线程安全的
     * 使用synchronized保证并发安全
     */
    private final Object indexLock = new Object();

    /**
     * HNSW图结构
     *
     * 存储结构：邻接表形式
     * - Key: 缓存Key（节点ID）
     * - Value: 该节点的邻居节点列表（Entry对象，包含Key和向量）
     *
     * HNSW图特点：
     * - 有多层结构（但本实现简化为单层）
     * - 每层是近似K近邻图
     * - 搜索时从上层开始，逐层向下搜索
     */
    private final Map<String, List<Entry>> graph = new ConcurrentHashMap<>();

    // ========== 构造函数 ==========

    /**
     * 构造函数 - 初始化HNSW缓存提供者
     *
     * 初始化步骤：
     * 1. 保存缓存配置引用
     * 2. 创建Caffeine响应缓存
     * 3. 输出初始化日志
     *
     * @param config 缓存配置对象
     */
    public HNSWCacheProvider(CacheConfig config) {
        this.config = config;

        // 创建Caffeine缓存实例
        this.responseCache = Caffeine.newBuilder()
                .maximumSize(config.getMaxSize())          // 最大缓存条目数
                .expireAfterWrite(config.getTtlSeconds(), TimeUnit.SECONDS)  // 写入后TTL过期
                .recordStats()                              // 记录统计信息（命中率等）
                .build();

        log.info("HNSW语义缓存初始化完成 (in-memory实现)，维度: {}, M: {}, EF: {}",
                HNSW_DIMENSION, MAX_CONNECTIONS, EF_SEARCH);
    }

    // ========== AICacheProvider接口实现 ==========

    /**
     * 返回缓存类型标识
     *
     * @return "hnsw" - 用于在AICacheManager中标识此缓存类型
     */
    @Override
    public String cacheType() {
        return "hnsw";
    }

    /**
     * 根据缓存Key获取AI响应
     *
     * @param cacheKey 缓存Key（SHA-256哈希）
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
     * 2. 生成查询文本对应的向量
     * 3. 将向量加入embeddingCache
     * 4. 将节点插入HNSW图结构
     *
     * 注意：步骤2-4需要获取indexLock保证线程安全
     *
     * @param cacheKey 缓存Key
     * @param response AI响应对象
     * @param ttlMs 过期时间（毫秒），本实现使用config中的TTL
     */
    @Override
    public void put(String cacheKey, AIResponse response, long ttlMs) {
        // 写入响应缓存
        responseCache.put(cacheKey, response);

        // 生成该缓存Key对应的向量表示
        float[] embedding = generateEmbedding(cacheKey);

        // 同步写入向量缓存和HNSW图
        synchronized (indexLock) {
            embeddingCache.put(cacheKey, embedding);
            insertToGraph(cacheKey, embedding);
        }

        log.debug("HNSW缓存写入: {}", maskKey(cacheKey));
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
     * 生成算法：
     * 1. 规范化请求文本：模型名 + 所有消息的角色:内容拼接
     * 2. 对规范化文本计算SHA-256哈希
     * 3. 返回十六进制哈希字符串作为缓存Key
     *
     * 相同请求（相同模型+相同消息）会产生相同的Key
     *
     * @param request AI请求对象
     * @return SHA-256哈希后的缓存Key
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

    // ========== 相似度搜索方法 ==========

    /**
     * 查找与给定Key最相似的缓存Key
     *
     * 算法流程：
     * 1. 将查询Key转换为向量
     * 2. 在HNSW图中搜索最近的邻居
     * 3. 计算余弦相似度
     * 4. 返回相似度>=阈值的最近邻Key
     *
     * 注意：本实现简化了HNSW搜索，直接遍历所有向量找最相似
     * 实际HNSW实现应使用多层图的贪婪搜索
     *
     * @param queryKey 查询Key
     * @return 最相似的缓存Key，若无相似度>=阈值则返回null
     */
    public String findSimilar(String queryKey) {
        // 第1步：生成查询向量
        float[] queryEmbedding = generateEmbedding(queryKey);

        synchronized (indexLock) {
            // 初始化优先队列，用于存储候选节点（按相似度降序排列）
            PriorityQueue<Entry> candidates = new PriorityQueue<>(
                    Comparator.comparingDouble(e -> -cosineSimilarity(queryEmbedding, e.embedding))
            );

            String currentBest = null;
            double bestSimilarity = -1;

            // 第2步：遍历所有缓存向量，找最相似的
            // 注意：本实现为简化版，实际应使用HNSW图的贪婪搜索
            for (String key : embeddingCache.keySet()) {
                // 计算查询向量与当前向量的余弦相似度
                double sim = cosineSimilarity(queryEmbedding, embeddingCache.get(key));

                // 更新最相似结果
                if (sim > bestSimilarity) {
                    bestSimilarity = sim;
                    currentBest = key;
                }
            }

            // 第3步：检查相似度是否达到阈值
            if (currentBest != null && bestSimilarity >= config.getSimilarityThreshold()) {
                log.info("HNSW缓存命中! 相似度: {:.3f}, key: {}", bestSimilarity, maskKey(currentBest));
                return currentBest;
            }
        }
        return null;
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

    // ========== HNSW图操作方法 ==========

    /**
     * 将新节点插入HNSW图
     *
     * 插入算法（简化版HNSW）：
     * 1. 创建新节点的空邻居列表
     * 2. 如果图为空，直接返回
     * 3. 使用searchLayer搜索最近的M个邻居
     * 4. 建立新节点到邻居的反向连接
     *
     * @param key 新节点ID（缓存Key）
     * @param embedding 新节点的向量表示
     */
    private void insertToGraph(String key, float[] embedding) {
        // 第1步：创建新节点的空邻居列表
        graph.put(key, new ArrayList<>());

        // 第2步：如果图中只有1个或0个节点，无需建立连接
        if (graph.size() <= 1) {
            return;
        }

        // 第3步：搜索最近的M个邻居
        PriorityQueue<Entry> searchResult = searchLayer(embedding, 1, EF_CONSTRUCTION);
        List<Entry> neighbors = new ArrayList<>();
        int k = Math.min(MAX_CONNECTIONS, searchResult.size());

        // 取前M个最近邻作为邻居
        for (int i = 0; i < k && !searchResult.isEmpty(); i++) {
            neighbors.add(searchResult.poll());
        }

        // 第4步：更新新节点的邻居列表
        graph.put(key, neighbors);

        // 第5步：建立反向连接（邻居节点也连接回新节点）
        for (Entry neighbor : neighbors) {
            List<Entry> neighborList = graph.get(neighbor.key);
            if (neighborList == null) {
                neighborList = new ArrayList<>();
                graph.put(neighbor.key, neighborList);
            }
            // 保持每个节点的连接数不超过MAX_CONNECTIONS
            if (neighborList.size() < MAX_CONNECTIONS) {
                neighborList.add(new Entry(key, embedding));
            }
        }
    }

    /**
     * 在HNSW图的一层中搜索最近的邻居
     *
     * 算法：贪婪搜索
     * 1. 初始化：取任意节点作为起始
     * 2. 贪心扩展：从当前最近邻的邻居中找更近的
     * 3. 维护候选队列：保存ef个最接近的候选
     * 4. 终止条件：队列中所有节点的邻居都比当前最远候选更远
     *
     * @param queryEmbedding 查询向量
     * @param layer 搜索层（本实现固定为1）
     * @param ef 搜索范围（候选队列大小）
     * @return 按相似度降序排列的优先队列
     */
    private PriorityQueue<Entry> searchLayer(float[] queryEmbedding, int layer, int ef) {
        // 候选队列：按相似度降序排列
        PriorityQueue<Entry> candidates = new PriorityQueue<>(
                Comparator.comparingDouble(e -> -cosineSimilarity(queryEmbedding, e.embedding))
        );

        // 已访问节点集合（防止重复访问）
        Set<String> visited = new HashSet<>();

        // 第1步：初始化，选择图中的第一个节点作为起始
        String enterKey = graph.keySet().iterator().next();
        float[] enterEmbedding = embeddingCache.get(enterKey);
        if (enterEmbedding == null) {
            return candidates;
        }

        // 添加起始节点到候选队列和已访问集合
        candidates.add(new Entry(enterKey, enterEmbedding));
        visited.add(enterKey);

        // 搜索结果队列：按相似度升序排列（用于返回最相似的）
        PriorityQueue<Entry> topCandidates = new PriorityQueue<>(
                Comparator.comparingDouble(e -> cosineSimilarity(queryEmbedding, e.embedding))
        );

        // 第2步：贪婪搜索循环
        while (!candidates.isEmpty()) {
            // 取出当前候选节点
            Entry current = candidates.poll();
            topCandidates.add(current);

            // 维护topCandidates大小不超过ef
            if (topCandidates.size() > ef) {
                topCandidates.poll();
            }

            // 获取当前候选节点的邻居
            float[] currentEmbedding = embeddingCache.get(current.key);
            if (currentEmbedding == null) {
                continue;
            }

            // 计算当前候选与查询的相似度
            double currentDist = cosineSimilarity(queryEmbedding, currentEmbedding);
            double topDist = cosineSimilarity(queryEmbedding, topCandidates.peek().embedding);

            // 第3步：终止条件检查
            // 如果当前节点的邻居都比topCandidates中最远的还远，终止搜索
            if (currentDist < topDist - 0.01) {
                break;
            }

            // 第4步：扩展邻居
            List<Entry> neighbors = graph.get(current.key);
            if (neighbors == null) {
                continue;
            }

            for (Entry neighbor : neighbors) {
                if (!visited.contains(neighbor.key)) {
                    visited.add(neighbor.key);
                    float[] neighborEmbedding = embeddingCache.get(neighbor.key);
                    if (neighborEmbedding != null) {
                        double neighborDist = cosineSimilarity(queryEmbedding, neighborEmbedding);
                        // 如果邻居比topCandidates中最远的更近，加入候选队列
                        if (topCandidates.size() < ef || neighborDist < cosineSimilarity(queryEmbedding, topCandidates.peek().embedding)) {
                            candidates.add(new Entry(neighbor.key, neighborEmbedding));
                        }
                    }
                }
            }
        }

        return topCandidates;
    }

    // ========== 向量处理方法 ==========

    /**
     * 从文本生成128维向量（嵌入向量）
     *
     * 算法说明：
     * 1. 使用文本的哈希值作为随机种子，保证相同文本产生相同向量
     * 2. 使用高斯分布生成向量元素（均值0，标准差0.1）
     * 3. L2归一化，使向量长度为1
     *
     * 注意：这是简化实现，实际应使用真实的嵌入模型（如OpenAI text-embedding-ada-002）
     *
     * @param text 输入文本
     * @return 128维归一化向量
     */
    private float[] generateEmbedding(String text) {
        // 创建128维向量
        float[] embedding = new float[HNSW_DIMENSION];

        // 使用文本哈希值作为随机种子
        long hash = text.hashCode();
        Random random = new Random(hash);

        // 使用高斯分布生成向量元素
        for (int i = 0; i < HNSW_DIMENSION; i++) {
            embedding[i] = (float) (random.nextGaussian() * 0.1);
        }

        // 计算L2范数
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        // 归一化：使向量长度为1
        if (norm > 0) {
            for (int i = 0; i < HNSW_DIMENSION; i++) {
                embedding[i] /= norm;
            }
        }

        return embedding;
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * 公式：cosine_similarity(A,B) = (A·B) / (||A|| × ||B||)
     * 由于向量已归一化，简化为：A·B
     *
     * 余弦相似度范围：-1到1
     * 1表示完全相同，0表示正交，-1表示完全相反
     *
     * @param a 向量A
     * @param b 向量B
     * @return 余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;

        // 计算点积和各向量的范数
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];    // 累加点积
            normA += a[i] * a[i];  // 累加A的平方
            normB += b[i] * b[i];  // 累加B的平方
        }

        // 防御性检查：避免除零
        if (normA == 0 || normB == 0) {
            return 0;
        }

        // 返回余弦相似度
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ========== 辅助方法 ==========

    /**
     * 脱敏缓存Key（用于日志输出）
     *
     * @param key 原始Key
     * @return 脱敏后的Key（显示前8位+省略号）
     */
    private String maskKey(String key) {
        if (key == null || key.length() < 16) {
            return "****";
        }
        return key.substring(0, 8) + "...";
    }

    /**
     * 获取缓存条目数
     *
     * @return 近似缓存大小
     */
    @Override
    public long size() {
        return responseCache.estimatedSize();
    }

    /**
     * 清空所有缓存
     *
     * 清空内容：
     * - 响应缓存
     * - 向量缓存
     * - HNSW图结构
     */
    @Override
    public void clear() {
        responseCache.invalidateAll();
        embeddingCache.clear();
        graph.clear();
    }

    /**
     * 获取相似度阈值
     *
     * @return 相似度阈值（通常为0.95）
     */
    public double getSimilarityThreshold() {
        return config.getSimilarityThreshold();
    }

    // ========== 内部类 ==========

    /**
     * HNSW图节点条目
     *
     * 包含：
     * - key: 节点ID（缓存Key）
     * - embedding: 节点对应的向量
     *
     * 使用Java record实现（简洁的不可变数据类）
     */
    private record Entry(String key, float[] embedding) {}
}
