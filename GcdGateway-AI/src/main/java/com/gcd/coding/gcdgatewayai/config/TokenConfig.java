package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;

/**
 * Token限流配置 - 控制AI请求的Token级别限流
 *
 * 功能说明：
 * 配置AI网关的Token级别限流规则，包括每分钟和每日的Token消耗限额。
 * 当某用户的Token消耗超过限额时，请求会被拒绝并返回429状态码。
 *
 * 限流维度：
 * - 用户维度（userId）：每个用户有独立的限额
 * - 模型维度（model）：可以针对特定模型设置限额
 * - 时间维度：每分钟和每日两个时间窗口
 *
 * 限流实现：
 * - 使用ConcurrentHashMap存储计数器和重置时间
 * - 每分钟/每日自动重置计数器
 * - 前置过滤器检查是否超限，超限则短路拒绝
 *
 * 使用场景：
 * - AITokenFilter：前置阶段检查Token限额
 * - AITokenPostCorrectionFilter：后置阶段修正配额
 *
 * @see AITokenFilter Token限流过滤器
 */
@Data
public class TokenConfig {

    /** 每分钟Token限额，默认100000 */
    private int defaultRateLimitPerMinute = 100000;

    /** 每日Token限额，默认10000000（千万） */
    private int defaultRateLimitPerDay = 10000000;

    /** Token限流开关，默认为false（未启用） */
    private boolean tokenRateLimitEnabled = false;

}