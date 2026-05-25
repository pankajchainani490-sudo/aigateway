package com.gcd.coding.gcdgatewayai.filter;

// ========== 导入部分 ==========

// AI网关配置管理器 - 获取AI模块全局配置
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfigManager;

// AI网关配置 - 包含AI模块开关、Token配置等
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfig;

// Token配置 - 包含限流开关、限额阈值等
import com.gcd.coding.gcdgatewayai.config.TokenConfig;

// API-Key元数据 - 包含用户配额信息
import com.gcd.coding.gcdgatewayai.metadata.ApiKeyMetadata;

// AI请求模型 - 用户发送的AI请求
import com.gcd.coding.gcdgatewayai.model.AIRequest;

// AI响应模型 - AI服务返回的响应
import com.gcd.coding.gcdgatewayai.model.AIResponse;

// Token计数器 - 使用jtokkit库估算Token数量
import com.gcd.coding.gcdgatewayai.token.TokenCounter;

// 网关上下文 - 存储请求/响应信息
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

// 过滤器接口
import com.gcd.coding.gcdgatewaycore.filter.Filter;

// 网关响应 - 用于构建限流响应
import com.gcd.coding.gcdgatewaycore.response.GatewayResponse;

// Netty HTTP相关类 - 用于设置响应头和状态码
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;

// Lombok日志注解
import lombok.extern.slf4j.Slf4j;

// Java并发工具类
import java.util.Map;                           // 存储计数器映射
import java.util.concurrent.ConcurrentHashMap;    // 线程安全HashMap
import java.util.concurrent.atomic.AtomicLong;    // 原子长整型计数器

// 过滤器名称和顺序常量
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_TOKEN_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_TOKEN_FILTER_ORDER;

/**
 * AI Token过滤器 - Token计数与分布式限流
 *
 * 功能说明：
 * 1. 前置估算：使用jtokkit快速估算输入Token数（pre-estimation）
 * 2. 分布式流控：基于Token数的分钟级和日级限流
 * 3. 配额扣减：预扣减用户配额（多退少补）
 *
 * 与AITokenPostCorrectionFilter配合：
 * - AITokenFilter：前置阶段预扣Token配额
 * - AITokenPostCorrectionFilter：后置阶段根据实际Token数修正配额
 *
 * 过滤器顺序：Integer.MIN_VALUE + 4，在AI_CACHE_FILTER之后，AI_PROTOCOL_FILTER之前
 *
 * 限流维度：
 * - 用户维度（userId）
 * - 模型维度（model）
 * - 时间维度（分钟/日）
 */
@Slf4j
public class AITokenFilter implements Filter {

    // ========== 计数器定义 ==========

    /**
     * 分钟级Token计数器
     *
     * Key格式：userId:model
     * 示例：user_12345678:gpt-4
     *
     * 用于统计每个用户每分钟的Token消耗总量
     * 超过阈值时触发分钟级限流
     */
    private final Map<String, AtomicLong> minuteTokenCounters = new ConcurrentHashMap<>();

    /**
     * 日级Token计数器
     *
     * Key格式：userId:model
     * 示例：user_12345678:claude-3
     *
     * 用于统计每个用户每日的Token消耗总量
     * 超过阈值时触发日级限流
     */
    private final Map<String, AtomicLong> dayTokenCounters = new ConcurrentHashMap<>();

    /**
     * 分钟级计数器重置时间记录
     *
     * 存储每个计数器key的最后重置时间
     * 用于判断是否需要重置计数器（每分钟重置）
     */
    private final Map<String, Long> minuteResetTimes = new ConcurrentHashMap<>();

    /**
     * 日级计数器重置时间记录
     *
     * 存储每个计数器key的最后重置时间
     * 用于判断是否需要重置计数器（每日重置）
     */
    private final Map<String, Long> dayResetTimes = new ConcurrentHashMap<>();

    // ========== 时间常量 ==========

    /** 一分钟的毫秒数 */
    private static final long MINUTE_MS = 60_000L;

    /** 一天的毫秒数 */
    private static final long DAY_MS = 86_400_000L;

    // ========== 前置过滤器方法 ==========

    /**
     * 前置过滤器方法 - Token前置估算与限流检查
     *
     * 执行流程：
     * 1. 检查AI模块是否启用
     * 2. 提取AI请求和用户身份
     * 3. 使用jtokkit估算输入Token数（前置估算）
     * 4. 检查是否超出分钟/日限额
     * 5. 预扣Token配额（累加到计数器）
     * 6. 将预估算Token数存入上下文（供后置修正使用）
     *
     * 限流逻辑：
     * - 分钟限流：当前已用Token + 本次申请Token > 分钟限额 → 拒绝
     * - 日限流：当前已用Token + 本次申请Token > 日限额 → 拒绝
     *
     * @param context 网关上下文
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 第1步：获取AI模块配置
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();

        // 如果AI模块未启用，直接跳过
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        // 第2步：获取AI请求对象
        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        if (aiRequest == null) {
            context.doFilter();
            return;
        }

        // 第3步：前置估算输入Token数
        // 使用jtokkit库快速估算（非精确但快速）
        int inputTokens = TokenCounter.countInputTokens(aiRequest);
        context.setAiRequest(aiRequest);

        // 第4步：获取用户和模型信息
        String model = aiRequest.getModel() != null ? aiRequest.getModel() : "default";
        String userId = getUserId(context);

        // 第5步：Token限流检查
        TokenConfig tokenConfig = aiConfig.getToken();
        if (tokenConfig.isTokenRateLimitEnabled()) {
            // 构建计数器的Key（用户:模型）
            String minuteKey = userId + ":" + model;
            String dayKey = userId + ":" + model;

            // 获取或创建分钟/日计数器
            AtomicLong minuteCounter = minuteTokenCounters.computeIfAbsent(minuteKey, k -> new AtomicLong());
            AtomicLong dayCounter = dayTokenCounters.computeIfAbsent(dayKey, k -> new AtomicLong());

            // 获取当前时间和上次重置时间
            long now = System.currentTimeMillis();
            long minuteReset = minuteResetTimes.computeIfAbsent(minuteKey, k -> now);
            long dayReset = dayResetTimes.computeIfAbsent(dayKey, k -> now);

            // 第6步：检查是否需要重置分钟计数器
            if (now - minuteReset > MINUTE_MS) {
                // 重置分钟计数器
                minuteTokenCounters.put(minuteKey, new AtomicLong());
                minuteResetTimes.put(minuteKey, now);
                minuteCounter = minuteTokenCounters.get(minuteKey);
            }

            // 第7步：检查是否需要重置日计数器
            if (now - dayReset > DAY_MS) {
                // 重置日计数器
                dayTokenCounters.put(dayKey, new AtomicLong());
                dayResetTimes.put(dayKey, now);
                dayCounter = dayTokenCounters.get(dayKey);
            }

            // 第8步：分钟级限流检查
            if (minuteCounter.get() + inputTokens > tokenConfig.getDefaultRateLimitPerMinute()) {
                log.warn("用户 {} 模型 {} 每分钟Token限额({})已达, 当前: {}, 申请: {}",
                        userId, model, tokenConfig.getDefaultRateLimitPerMinute(),
                        minuteCounter.get(), inputTokens);
                sendRateLimitResponse(context, "Minute rate limit exceeded");
                return;
            }

            // 第9步：日级限流检查
            if (dayCounter.get() + inputTokens > tokenConfig.getDefaultRateLimitPerDay()) {
                log.warn("用户 {} 模型 {} 每日Token限额({})已达, 当前: {}, 申请: {}",
                        userId, model, tokenConfig.getDefaultRateLimitPerDay(),
                        dayCounter.get(), inputTokens);
                sendRateLimitResponse(context, "Daily rate limit exceeded");
                return;
            }

            // 第10步：预扣Token配额（累加到计数器）
            minuteCounter.addAndGet(inputTokens);
            dayCounter.addAndGet(inputTokens);

            // 第11步：保存预估算Token数到上下文（供后置修正使用）
            context.setPreEstimatedTokens(inputTokens);
        }

        log.debug("AI请求输入Token前置估算: {}, 用户: {}, 模型: {}", inputTokens, userId, model);
        context.doFilter();
    }

    // ========== 后置过滤器方法 ==========

    /**
     * 后置过滤器方法 - Token配额修正
     *
     * 执行时机：AI服务返回响应后执行
     *
     * 执行流程：
     * 1. 获取AI响应和预估算Token数
     * 2. 解析实际输出Token数
     * 3. 计算差额（多退少补）
     * 4. 调用ApiKeyMetadata修正配额
     *
     * 修正逻辑：
     * - 预估算 < 实际：少扣了，需要再扣差额
     * - 预估算 > 实际：多扣了，需要退还差额
     *
     * @param context 网关上下文
     */
    @Override
    public void doPostFilter(GatewayContext context) {
        // 第1步：获取AI响应
        AIResponse aiResponse = context.getAiResponse(AIResponse.class);
        if (aiResponse != null) {
            // 第2步：计算实际输出Token数
            int outputTokens = TokenCounter.countOutputTokens(aiResponse);

            // 第3步：获取前置预估算的Token数
            int preEstimated = context.getPreEstimatedTokens();

            // 第4步：计算差额
            int difference = outputTokens - preEstimated;

            // 如果有差额，记录日志
            if (difference != 0) {
                log.info("Token修正: 预估算 {} vs 实际输出 {} (差异: {})",
                        preEstimated, outputTokens, difference);
            }

            // 第5步：获取ApiKeyMetadata，修正配额
            ApiKeyMetadata metadata = context.getApiKeyMetadata(ApiKeyMetadata.class);
            if (metadata != null && difference != 0) {
                // 获取模型名称
                String model = context.getAiRequest(AIRequest.class) != null
                        ? context.getAiRequest(AIRequest.class).getModel() : "default";

                // 调用incrementQuota修正配额
                // 注意：incrementQuota的正数表示增加，负数表示减少
                // 所以传-difference实现多退少补
                metadata.incrementQuota(-difference, model);
            }

            log.debug("AI响应输出Token计数: {}", outputTokens);
        }

        context.doFilter();
    }

    // ========== Filter接口实现 ==========

    /**
     * 返回过滤器唯一标识
     *
     * @return 过滤器名称 "ai_token_filter"
     */
    @Override
    public String mark() {
        return AI_TOKEN_FILTER_NAME;
    }

    /**
     * 返回过滤器执行顺序
     *
     * @return 过滤器顺序常量，值为Integer.MIN_VALUE + 4
     */
    @Override
    public int getOrder() {
        return AI_TOKEN_FILTER_ORDER;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从上下文获取用户ID
     *
     * 优先级：
     * 1. 从ApiKeyMetadata获取userId
     * 2. 如果没有则返回"anonymous"（匿名用户）
     *
     * @param context 网关上下文
     * @return 用户ID字符串
     */
    private String getUserId(GatewayContext context) {
        // 尝试从ApiKeyMetadata获取用户ID
        ApiKeyMetadata metadata = context.getApiKeyMetadata(ApiKeyMetadata.class);
        if (metadata != null) {
            return metadata.getUserId();
        }
        // 降级为匿名用户
        return "anonymous";
    }

    /**
     * 发送限流响应
     *
     * 当用户Token配额超限时，返回429 Too Many Requests响应
     *
     * @param context 网关上下文
     * @param message 限流错误消息
     */
    private void sendRateLimitResponse(GatewayContext context, String message) {
        // 构建HTTP响应
        GatewayResponse response = new GatewayResponse();

        // 设置响应头
        response.addHeader(HttpHeaderNames.CONTENT_TYPE,
                HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");

        // 设置HTTP状态码：429 Too Many Requests
        response.setHttpResponseStatus(HttpResponseStatus.TOO_MANY_REQUESTS);

        // 设置响应体
        response.setContent("{\"error\":\"" + message + "\"}");

        // 存入上下文
        context.setResponse(response);

        // 启用短路，不再执行后续过滤器
        context.setShortCircuit(true);
    }
}
