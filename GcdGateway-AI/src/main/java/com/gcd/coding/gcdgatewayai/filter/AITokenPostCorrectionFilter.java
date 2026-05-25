package com.gcd.coding.gcdgatewayai.filter;

// ========== 导入部分 ==========

// API-Key元数据 - 用于修正用户配额
import com.gcd.coding.gcdgatewayai.metadata.ApiKeyMetadata;

// AI请求模型 - 用于获取模型名称
import com.gcd.coding.gcdgatewayai.model.AIRequest;

// AI响应模型 - 用于解析实际Token消耗
import com.gcd.coding.gcdgatewayai.model.AIResponse;

// Token计数器 - 用于计算实际Token数
import com.gcd.coding.gcdgatewayai.token.TokenCounter;

// 网关上下文 - 存储请求/响应信息
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

// 过滤器接口
import com.gcd.coding.gcdgatewaycore.filter.Filter;

// Lombok日志注解
import lombok.extern.slf4j.Slf4j;

// Java并发工具类
import java.util.Map;                               // 存储待修正记录
import java.util.concurrent.ConcurrentHashMap;        // 线程安全HashMap
import java.util.concurrent.Executors;              // 线程池工厂
import java.util.concurrent.ScheduledExecutorService; // 调度线程池
import java.util.concurrent.TimeUnit;                // 时间单位

// 过滤器常量
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.TOKEN_POST_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.TOKEN_POST_FILTER_ORDER;

/**
 * Token后置修正过滤器
 *
 * 功能说明：
 * 在AI服务返回响应后，对预扣的Token配额进行修正（多退少补）。
 * 这是"Token前置估算与后置修正"机制的后半部分。
 *
 * 工作原理：
 * 1. AITokenFilter在请求前置阶段预估算输入Token并预扣配额
 * 2. AI服务返回后，此过滤器计算实际Token消耗（输入+输出）
 * 3. 比较预估算与实际值的差异
 * 4. 如果差异超过阈值（10 Tokens），异步执行配额修正
 *
 * 为什么要异步执行？
 * - 避免阻塞主请求线程
 * - 对于SSE流式响应，只有在最后才能获得完整Token统计
 * - 不影响客户端首次响应的延迟
 *
 * 过滤器顺序：Integer.MAX_VALUE - 1（倒数第二个，在ROUTE_FILTER之前）
 *
 * 修正公式：
 * - difference = actualTotal - preEstimated
 * - if difference > 0: 少扣了，需要再扣difference
 * - if difference < 0: 多扣了，需要退还|difference|
 *
 * 示例：
 * - 预估算：100 Tokens（只算了输入）
 * - 实际：输入50 + 输出150 = 200 Tokens
 * - 差额：200 - 100 = 100（少扣了100）
 * - 修正：再扣100 Tokens
 */
@Slf4j
public class AITokenPostCorrectionFilter implements Filter {

    // ========== 异步修正组件 ==========

    /**
     * 调度线程池
     *
     * 用途：异步执行配额修正，避免阻塞主请求处理
     * 配置：4个线程，可处理并发修正请求
     */
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * 待修正记录映射
     *
     * Key: 请求ID（用于唯一标识一次请求）
     * Value: 待修正的TokenCorrection记录
     *
     * 使用ConcurrentHashMap保证线程安全
     * 修正完成后从Map中移除
     */
    private final Map<String, TokenCorrection> pendingCorrections = new ConcurrentHashMap<>();

    // ========== 构造函数 ==========

    /**
     * 默认构造函数
     */
    public AITokenPostCorrectionFilter() {
        // 初始化在线程池创建时完成
    }

    // ========== 过滤器方法 ==========

    /**
     * 前置过滤器方法
     *
     * 说明：此过滤器的前置阶段不做任何处理
     * 原因：Token修正只能在响应返回后进行
     *
     * @param context 网关上下文
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 透传，不做任何处理
        context.doFilter();
    }

    /**
     * 后置过滤器方法 - Token配额修正
     *
     * 执行时机：AI服务返回完整响应后（包括SSE流式响应的最后一块）
     *
     * 执行流程：
     * 1. 获取AI响应和请求对象
     * 2. 解析实际输入Token数（jtokkit精确计算）
     * 3. 解析实际输出Token数（从响应usage字段）
     * 4. 计算总实际Token数
     * 5. 与前置预估算对比，计算差额
     * 6. 差额超过阈值时，异步执行修正
     *
     * 修正阈值：
     * - 只有当差额绝对值 > 10 Tokens时才修正
     * - 避免对微小差异的过度修正
     *
     * @param context 网关上下文
     */
    @Override
    public void doPostFilter(GatewayContext context) {
        // 第1步：获取AI响应和请求对象
        AIResponse aiResponse = context.getAiResponse(AIResponse.class);
        AIRequest aiRequest = context.getAiRequest(AIRequest.class);

        // 如果响应或请求为空，直接跳过
        if (aiResponse == null || aiRequest == null) {
            context.doFilter();
            return;
        }

        // 第2步：计算实际输入Token数（精确计算，使用jtokkit）
        int preEstimated = context.getPreEstimatedTokens();
        int actualInput = TokenCounter.countInputTokens(aiRequest);

        // 第3步：计算实际输出Token数（从响应中解析）
        int actualOutput = TokenCounter.countOutputTokens(aiResponse);

        // 第4步：计算实际总Token数
        int actualTotal = actualInput + actualOutput;

        // 第5步：获取请求ID作为修正记录的Key
        String correctionKey = context.getRequest().getId();

        // 第6步：检查是否需要修正（差额超过阈值）
        if (preEstimated > 0 && actualTotal > 0) {
            // 计算差额：实际 - 预估算
            int difference = actualTotal - preEstimated;

            // 只有差额绝对值超过10时才修正
            if (Math.abs(difference) > 10) {
                log.info("Token后置修正 [{}]: 预估算 {} vs 实际 {} (差异: {})",
                        correctionKey, preEstimated, actualTotal, difference);

                // 第7步：记录待修正信息
                pendingCorrections.put(correctionKey, new TokenCorrection(
                        context.getApiKeyMetadata(ApiKeyMetadata.class),  // API-Key元数据
                        aiRequest.getModel(),                            // 模型名称
                        preEstimated,                                    // 预估算Token数
                        actualTotal,                                     // 实际总Token数
                        System.currentTimeMillis()                        // 时间戳
                ));

                // 第8步：异步执行修正（延迟1秒，确保响应已完全发送）
                scheduler.schedule(() -> applyCorrection(correctionKey), 1, TimeUnit.SECONDS);
            }
        }

        context.doFilter();
    }

    // ========== 修正执行方法 ==========

    /**
     * 执行Token配额修正
     *
     * 由调度线程池异步调用
     *
     * 执行流程：
     * 1. 从待修正Map中移除记录
     * 2. 检查记录是否存在
     * 3. 获取API-Key元数据
     * 4. 计算差额
     * 5. 调用ApiKeyMetadata.incrementQuota()修正配额
     *
     * @param correctionKey 待修正记录的Key（请求ID）
     */
    private void applyCorrection(String correctionKey) {
        // 第1步：获取并移除待修正记录
        TokenCorrection correction = pendingCorrections.remove(correctionKey);
        if (correction == null) {
            // 记录已被处理或不存在，直接返回
            return;
        }

        // 第2步：获取API-Key元数据
        ApiKeyMetadata metadata = correction.metadata;
        if (metadata == null) {
            // 元数据为空（可能API-Key无效），直接返回
            return;
        }

        // 第3步：计算差额
        int difference = correction.actualTotal - correction.preEstimated;
        if (difference == 0) {
            // 无差额，无需修正
            return;
        }

        // 第4步：执行配额修正
        // incrementQuota接收正数表示增加配额，负数表示减少配额
        // 所以传-difference：多扣了（负差额）则退还，少扣了（正差额）则再扣
        metadata.incrementQuota(-difference, correction.model);

        // 第5步：记录修正日志
        log.info("Token配额修正已应用 [{}]: 用户 {} 模型 {} 修正量 {} (预估算: {}, 实际: {})",
                correctionKey,
                metadata.getUserId(),
                correction.model,
                difference,
                correction.preEstimated,
                correction.actualTotal);
    }

    // ========== Filter接口实现 ==========

    /**
     * 返回过滤器唯一标识
     *
     * @return 过滤器名称 "token_post_filter"
     */
    @Override
    public String mark() {
        return TOKEN_POST_FILTER_NAME;
    }

    /**
     * 返回过滤器执行顺序
     *
     * @return Integer.MAX_VALUE - 1（倒数第二个过滤器）
     */
    @Override
    public int getOrder() {
        return TOKEN_POST_FILTER_ORDER;
    }

    // ========== 内部类 ==========

    /**
     * Token修正记录
     *
     * 用于保存待修正的Token信息，由调度线程池异步处理
     *
     * @param metadata API-Key元数据（用户配额信息）
     * @param model 模型名称
     * @param preEstimated 前置阶段预估算的Token数
     * @param actualTotal 后置阶段计算的实际总Token数
     * @param timestamp 修正记录创建时间戳
     */
    private record TokenCorrection(
            ApiKeyMetadata metadata,  // API-Key元数据
            String model,            // 模型名称
            int preEstimated,        // 预估算Token数
            int actualTotal,         // 实际总Token数
            long timestamp           // 时间戳
    ) {}
}
