package com.gcd.coding.gcdgatewayai.billing;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class AIBillingRecord {

    private String requestId;

    private String model;

    private String provider;

    private boolean cacheHit;

    private String cacheMode;

    private int inputTokens;

    private int outputTokens;

    private double inputCost;

    private double outputCost;

    private double totalCost;

    private long latencyMs;

    private String timestamp;

    private String clientIp;

    private String billingType;

    public AIBillingRecord() {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }

    public void calculateCost(double inputPricePer1K, double outputPricePer1K, double cacheDiscount) {
        if (cacheHit) {
            this.inputCost = inputTokens / 1000.0 * inputPricePer1K * cacheDiscount;
            this.outputCost = 0;
            this.billingType = "缓存命中";
        } else {
            this.inputCost = inputTokens / 1000.0 * inputPricePer1K;
            this.outputCost = outputTokens / 1000.0 * outputPricePer1K;
            this.billingType = "缓存未命中";
        }
        this.totalCost = this.inputCost + this.outputCost;
    }

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
