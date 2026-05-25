package com.gcd.coding.gcdgatewayai.filter;

// ========== 导入部分 ==========

// Guava缓存库 - 用于存储已确认无效的API-Key
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

// Guava布隆过滤器 - 高效的概率数据结构，用于快速判断元素是否存在
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

// 网关上下文 - 存储请求/响应信息
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

// 过滤器接口 - 所有过滤器必须实现
import com.gcd.coding.gcdgatewaycore.filter.Filter;

// Lombok日志注解
import lombok.extern.slf4j.Slf4j;

// Java NIO字符编码
import java.nio.charset.StandardCharsets;

// Java并发工具类
import java.util.concurrent.TimeUnit;

// 静态导入过滤器常量
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.*;

/**
 * 负缓存过滤器（防缓存穿透过滤器）
 *
 * 功能说明：
 * 使用布隆过滤器（BloomFilter）快速判断API-Key是否"绝对不存在"，
 * 从而快速拒绝无效请求，防止缓存穿透攻击。
 *
 * 工作原理：
 * 1. 布隆过滤器特点：
 *    - 返回"不存在" = 100%准确（绝不会误判）
 *    - 返回"可能存在" = 可能有极小概率误判（可配置，本实现0.01）
 * 2. 两级缓存机制：
 *    - 第一级：布隆过滤器 - 快速判断"绝对不存在"
 *    - 第二级：Guava Cache - 存储已确认无效的Key（5分钟过期）
 *
 * 与AuthFilter配合：
 * - NegativeCacheFilter先于AuthFilter执行（AUTH_FILTER_ORDER + 1）
 * - 快速拦截明显无效的Key，减少AuthFilter的计算开销
 *
 * 过滤器顺序：Integer.MIN_VALUE + 2，在AuthFilter之后
 */
@Slf4j
public class NegativeCacheFilter implements Filter {

    // ========== 常量定义 ==========

    /**
     * 期望存储的API-Key数量
     * 用于计算布隆过滤器的最优位数
     * 公式：m = -n * ln(p) / (ln(2)^2)
     * 其中n=100万，p=0.01
     */
    private static final int EXPECTED_API_KEYS = 1_000_000;

    /**
     * 布隆过滤器的期望误判率
     * 0.01 = 1%误判率
     * 注意：这是对"可能存在"的误判，"绝对不存在"不会误判
     *
     * 性能说明：
     * - 误判率越低，占用内存越大
     * - 0.01是性能和准确性的平衡点
     */
    private static final double FALSE_POSITIVE_RATE = 0.01;

    // ========== 过滤器组件 ==========

    /**
     * 布隆过滤器 - 存储所有有效的API-Key
     *
     * 判断结果：
     * - mightContain()返回false → API-Key绝对不存在，快速拦截
     * - mightContain()返回true  → API-Key可能存在（有小概率误判），需进一步验证
     *
     * 特点：
     * - 内存高效：约1.2MB（100万Key，1%误判率）
     * - 查询快速：O(1)时间复杂度
     * - 不可删除：布隆过滤器不支持删除操作
     */
    private final BloomFilter<String> validApiKeyBloomFilter;

    /**
     * 已确认无效Key的缓存
     *
     * 作用：解决布隆过滤器的误判问题
     * - 当一个Key被AuthFilter确认无效后，记录到此缓存
     * - 下次查询时直接从此缓存判断，避免误判影响
     *
     * 配置：
     * - 最大容量：10万条
     * - 过期时间：5分钟（防止无效数据永久占用）
     */
    private final Cache<String, Boolean> confirmedInvalidKeys;

    // ========== 构造函数 ==========

    /**
     * 默认构造函数 - 初始化布隆过滤器和负缓存
     */
    public NegativeCacheFilter() {
        // 创建布隆过滤器，使用UTF-8编码存储字符串
        // 参数：期望元素数量，期望误判率
        this.validApiKeyBloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_API_KEYS,
                FALSE_POSITIVE_RATE
        );

        // 创建Guava缓存，存储已确认无效的Key
        this.confirmedInvalidKeys = CacheBuilder.newBuilder()
                .maximumSize(100_000)           // 最大10万条
                .expireAfterWrite(5, TimeUnit.MINUTES)  // 写入5分钟后过期
                .build();

        log.info("NegativeCacheFilter (BloomFilter) 初始化完成，期望APIKey数量: {}, 误判率: {}",
                EXPECTED_API_KEYS, FALSE_POSITIVE_RATE);
    }

    // ========== 过滤器核心方法 ==========

    /**
     * 前置过滤器方法
     *
     * 执行流程：
     * 1. 提取请求中的API-Key
     * 2. 先查confirmedInvalidKeys（绝对准确）：
     *    - 命中 → 直接短路拦截
     * 3. 再查布隆过滤器（可能误判）：
     *    - 返回false → Key绝对不存在，记录到confirmedInvalidKeys，短路拦截
     *    - 返回true  → Key可能存在，放行到AuthFilter进一步验证
     *
     * 短路说明：
     * - 此过滤器设置shortCircuit=true时，AuthFilter不会被执行
     * - 因此无效Key会被快速拦截，不会浪费后续过滤器资源
     *
     * @param context 网关上下文
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 第1步：提取API-Key
        String apiKey = extractApiKey(context);

        // 如果没有API-Key，不做拦截（让AuthFilter处理）
        if (apiKey == null) {
            context.doFilter();
            return;
        }

        // 第2步：查confirmedInvalidKeys缓存（绝对准确）
        if (confirmedInvalidKeys.getIfPresent(apiKey) != null) {
            // 命中缓存：此Key之前已被确认无效，直接拦截
            log.debug("API key confirmed invalid (negative cache hit), short-circuiting");
            context.setShortCircuit(true);
            return;
        }

        // 第3步：查布隆过滤器（可能有误判）
        if (!validApiKeyBloomFilter.mightContain(apiKey)) {
            // 布隆过滤器返回false：Key绝对不存在（100%准确）
            log.debug("API key not in Bloom filter (definitely invalid), short-circuiting");

            // 记录到confirmedInvalidKeys，避免下次重复计算
            confirmedInvalidKeys.put(apiKey, Boolean.TRUE);

            // 短路拦截
            context.setShortCircuit(true);
            return;
        }

        // 第4步：布隆过滤器返回true：Key可能存在（有小概率误判）
        // 放行到AuthFilter进行最终验证
        log.debug("API key might be valid (Bloom filter positive), proceeding");
        context.doFilter();
    }

    /**
     * 后置过滤器方法
     *
     * 说明：此过滤器在postFilter阶段不做任何操作
     * 实际使用场景：可在此记录拦截统计、更新缓存等
     *
     * @param context 网关上下文
     */
    @Override
    public void doPostFilter(GatewayContext context) {
        // 透传，不做任何处理
        context.doFilter();
    }

    // ========== Filter接口实现 ==========

    /**
     * 返回过滤器唯一标识
     *
     * @return 过滤器名称 "negative_cache_filter"
     */
    @Override
    public String mark() {
        return "negative_cache_filter";
    }

    /**
     * 返回过滤器执行顺序
     *
     * 顺序规则：AUTH_FILTER_ORDER + 1 = 在AuthFilter之后执行
     * 原因：NegativeCacheFilter依赖AuthFilter的验证结果来更新confirmedInvalidKeys
     *
     * @return 过滤器顺序，比AuthFilter大1
     */
    @Override
    public int getOrder() {
        return AUTH_FILTER_ORDER + 1;
    }

    // ========== 公共方法 ==========

    /**
     * 注册有效的API-Key到布隆过滤器
     *
     * 使用场景：
     * 1. 管理后台创建新API-Key时调用
     * 2. 用户成功验证后异步调用
     *
     * 注意：
     * - 布隆过滤器不支持删除，只能添加
     * - 调用此方法后，之前标记为无效的Key可能被重新认为有效
     *
     * @param apiKey 有效的API-Key字符串
     */
    public void registerValidApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            // 添加到布隆过滤器
            validApiKeyBloomFilter.put(apiKey);

            // 从confirmedInvalidKeys中移除（如果存在）
            confirmedInvalidKeys.invalidate(apiKey);

            log.debug("Valid API key registered in Bloom filter");
        }
    }

    /**
     * 标记API-Key为无效（记录到负缓存）
     *
     * 使用场景：
     * 1. AuthFilter验证失败后调用
     * 2. 管理员禁用某API-Key时调用
     *
     * 注意：
     * - 此方法只是记录到confirmedInvalidKeys，不会从布隆过滤器中删除
     * - 下次查询时会先查confirmedInvalidKeys，避免误判
     *
     * @param apiKey 无效的API-Key字符串
     */
    public void markApiKeyInvalid(String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            confirmedInvalidKeys.put(apiKey, Boolean.TRUE);
            log.debug("API key marked as invalid in negative cache");
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从请求头中提取API-Key
     *
     * 提取顺序：
     * 1. X-API-Key 请求头
     * 2. Authorization: Bearer <token> 请求头
     *
     * @param context 网关上下文
     * @return API-Key字符串，若不存在返回null
     */
    private String extractApiKey(GatewayContext context) {
        // 优先从X-API-Key提取
        String apiKey = context.getRequest().getHeaders().get("X-API-Key");
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }

        // 次优从Authorization: Bearer提取
        String authHeader = context.getRequest().getHeaders().get("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        return null;
    }
}
