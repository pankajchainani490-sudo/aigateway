package com.gcd.coding.gcdgatewayai.billing;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AI计费记录 - 记录每次AI请求的计费信息
 *
 * 功能说明：
 * 用于记录和展示AI请求的详细计费信息，包括输入/输出Token数、费用计算、延迟等。
 * 支持缓存命中时的特殊计费规则（缓存命中时输出费用为0）。
 *
 * 计费逻辑：
 * - 缓存未命中：输入费用 = 输入Token数 / 1000 × 输入单价 + 输出Token数 / 1000 × 输出单价
 * - 缓存命中：输入费用 = 输入Token数 / 1000 × 输入单价 × 缓存折扣，输出费用 = 0
 *
 * 使用场景：
 * - AIBillingFilter：后置处理时创建计费记录并计算费用
 * - 日志输出：计费详情以格式化的方式输出到日志
 * - 账单系统：可将计费记录发送到外部账单系统
 */
@Data
public class AIBillingRecord {

    /** 请求唯一标识，用于追踪和关联日志 */
    private String requestId;

    /** 实际调用的模型名称，如 "deepseek-chat"、"gpt-4" */
    private String model;

    /** AI提供商名称，如 "deepseek"、"openai" */
    private String provider;

    /** 是否命中缓存，命中时输出费用享受折扣或为0 */
    private boolean cacheHit;

    /** 缓存模式，如 "exact"、"hnsw"、"embedding" */
    private String cacheMode;

    /** 输入Token数量（用户发送的消息） */
    private int inputTokens;

    /** 输出Token数量（AI生成的消息） */
    private int outputTokens;

    /** 输入费用（根据输入Token数和单价计算） */
    private double inputCost;

    /** 输出费用（根据输出Token数和单价计算） */
    private double outputCost;

    /** 合计费用 = 输入费用 + 输出费用 */
    private double totalCost;

    /** 请求耗时（毫秒），用于性能监控 */
    private long latencyMs;

    /** 记录创建时间，格式：yyyy-MM-dd HH:mm:ss.SSS */
    private String timestamp;

    /** 客户端IP地址，用于审计和追踪 */
    private String clientIp;

    /** 计费类型：缓存命中 / 缓存未命中 */
    private String billingType;

    /**
     * 默认构造函数
     * 初始化时间戳为当前时间
     */
    public AIBillingRecord() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }

    /**
     * 计算费用
     *
     * 计算规则：
     * - 缓存命中：输入费用 = 输入Token × 单价 × 折扣，输出费用 = 0
     * - 缓存未命中：输入费用 = 输入Token × 单价，输出费用 = 输出Token × 单价
     *
     * @param inputPricePer1K 输入价格（元/千Token）
     * @param outputPricePer1K 输出价格（元/千Token）
     * @param cacheDiscount 缓存命中折扣（0.0-1.0）
     */
    public void calculateCost(double inputPricePer1K, double outputPricePer1K, double cacheDiscount) {
        if (cacheHit) {
            // 缓存命中：输入费用打折，输出费用为0
            this.inputCost = inputTokens / 1000.0 * inputPricePer1K * cacheDiscount;
            this.outputCost = 0;
            this.billingType = "缓存命中";
        } else {
            // 缓存未命中：正常计费
            this.inputCost = inputTokens / 1000.0 * inputPricePer1K;
            this.outputCost = outputTokens / 1000.0 * outputPricePer1K;
            this.billingType = "缓存未命中";
        }
        this.totalCost = this.inputCost + this.outputCost;
    }

    /**
     * 返回格式化的计费明细字符串
     *
     * 用于日志输出，格式如下：
     * ╔═══════════════════════════ AI网关计费明细 ═══════════════════════════╗
     * ║ 请求ID   : xxx
     * ║ 时间     : xxx
     * ║ 模型     : xxx
     * ║ 提供商   : xxx
     * ║ 客户端IP : xxx
     * ╟─────────────────────────────────────────────────────────────────────╢
     * ║ 计费类型 : xxx
     * ║ 缓存模式 : xxx
     * ╟─────────────────────────────────────────────────────────────────────╢
     * ║ 输入Token : xxx
     * ║ 输出Token : xxx
     * ╟─────────────────────────────────────────────────────────────────────╢
     * ║ 输入费用 : xxx
     * ║ 输出费用 : xxx
     * ║ 合计费用 : xxx
     * ╟─────────────────────────────────────────────────────────────────────╢
     * ║ 耗时     : xxx ms
     * ╚═════════════════════════════════════════════════════════════════════╝
     *
     * @return 格式化的计费明细字符串
     */
    public String toDisplayString() {
        return String.format(
            "\n╔═══════════════════════════ AI网关计费明细 ═══════════════════════════╗\n" +
            "║ 请求ID   : %s\n" +
            "║ 时间     : %s\n" +
            "║ 模型     : %s\n" +
            "║ 提供商   : %s\n" +
            "║ 客户端IP : %s\n" +
            "╟─────────────────────────────────────────────────────────────────────╢\n" +
            "║ 计费类型 : %s\n" +
            "║ 缓存模式 : %s\n" +
            "╟─────────────────────────────────────────────────────────────────────╢\n" +
            "║ 输入Token : %d\n" +
            "║ 输出Token : %d\n" +
            "╟─────────────────────────────────────────────────────────────────────╢\n" +
            "║ 输入费用 : $%.6f\n" +
            "║ 输出费用 : $%.6f\n" +
            "║ 合计费用 : $%.6f\n" +
            "╟─────────────────────────────────────────────────────────────────────╢\n" +
            "║ 耗时     : %d ms\n" +
            "╚═════════════════════════════════════════════════════════════════════╝",
            requestId, timestamp, model, provider, clientIp,
            billingType, cacheMode != null ? cacheMode : "无",
            inputTokens, outputTokens,
            inputCost, outputCost, totalCost,
            latencyMs
        );
    }

}