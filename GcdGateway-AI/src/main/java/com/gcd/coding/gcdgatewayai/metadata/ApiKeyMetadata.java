package com.gcd.coding.gcdgatewayai.metadata;

import lombok.Data;

import java.util.Map;

/**
 * API-Key元数据类 - 存储与API-Key关联的用户和配额信息
 *
 * 功能说明：
 * 存储与API-Key关联的所有用户和配额信息，供过滤器链中的各个组件使用。
 * 该对象会被存入GatewayContext，在AI模块的各个过滤器间共享。
 *
 * 主要字段：
 * - userId：用户唯一标识符
 * - apiKey：API-Key本身
 * - quotaRemaining/quotaTotal：剩余/总配额
 * - planType：套餐类型（free/pro/enterprise）
 * - expiresAt：过期时间
 * - modelQuotas：各模型的独立配额
 *
 * 主要方法：
 * - isExpired()：检查是否过期
 * - hasQuota()：检查是否有可用配额
 * - decrementQuota()：扣减配额
 * - incrementQuota()：增加配额（修正用）
 *
 * 使用场景：
 * - AuthFilter：验证用户身份和套餐权限
 * - AITokenFilter：检查用户剩余配额
 * - QuotaWeightedLoadBalanceStrategy：根据配额分配负载权重
 * - AITokenPostCorrectionFilter：修正Token配额
 *
 * @see AuthFilter 鉴权过滤器
 * @see AITokenFilter Token限流过滤器
 * @see AITokenPostCorrectionFilter Token后置修正过滤器
 */
@Data
public class ApiKeyMetadata {

    // ========== 用户标识字段 ==========

    /**
     * 用户唯一标识符
     *
     * 格式示例：user_12345678
     * 用于日志追踪、配额统计、计费等
     */
    private String userId;

    /**
     * API-Key本身
     *
     * 格式示例：sk-pro-xxxx1234xxxx5678
     * 用于标识和验证请求来源
     */
    private String apiKey;

    // ========== 配额相关字段 ==========

    /**
     * 用户剩余配额（Token数量）
     *
     * 单位：Token数量（非请求次数）
     * 扣减规则：每次请求根据输入+输出Token数扣减
     */
    private int quotaRemaining;

    /**
     * 用户总配额（周期内总额度）
     *
     * 通常为日配额或月配额
     */
    private int quotaTotal;

    // ========== 套餐相关字段 ==========

    /**
     * 套餐类型
     *
     * 可选值：
     * - "free": 免费版
     * - "pro": 专业版
     * - "enterprise": 企业版
     */
    private String planType;

    /**
     * API-Key过期时间戳（毫秒）
     *
     * - 0或负数表示永不过期
     * - 大于0表示在指定时间后过期
     */
    private long expiresAt;

    // ========== 模型配额字段 ==========

    /**
     * 各模型的独立配额映射
     *
     * Key: 模型名称，如 "gpt-4"
     * Value: 该模型的剩余配额
     *
     * 某些高级套餐支持对特定模型设置独立配额
     */
    private Map<String, Integer> modelQuotas;

    // ========== 业务判断方法 ==========

    /**
     * 判断API-Key是否已过期
     *
     * @return true表示已过期，false表示未过期或永不过期
     */
    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    /**
     * 检查指定模型是否还有可用配额
     *
     * 判断逻辑：
     * 1. 若quotaRemaining <= 0，返回false
     * 2. 若modelQuotas包含该模型，检查模型配额 > 0
     * 3. 否则返回true（使用通用配额）
     *
     * @param model 模型名称
     * @return true表示有可用配额
     */
    public boolean hasQuota(String model) {
        // 检查通用配额
        if (quotaRemaining <= 0) {
            return false;
        }
        // 检查模型独立配额
        if (modelQuotas != null && modelQuotas.containsKey(model)) {
            return modelQuotas.get(model) > 0;
        }
        return true;
    }

    // ========== 配额操作方法 ==========

    /**
     * 扣减配额
     *
     * @param tokens 要扣除的Token数量
     * @param model 模型名称（用于扣减模型独立配额）
     */
    public void decrementQuota(int tokens, String model) {
        // 扣减通用配额
        if (quotaRemaining > 0) {
            quotaRemaining = Math.max(0, quotaRemaining - tokens);
        }
        // 扣减模型独立配额
        if (modelQuotas != null && modelQuotas.containsKey(model)) {
            int currentModelQuota = modelQuotas.get(model);
            modelQuotas.put(model, Math.max(0, currentModelQuota - tokens));
        }
    }

    /**
     * 增加配额（修正用）
     *
     * 用于：
     * - Token后置修正：实际Token数与预估值差异，多退少补
     * - 管理员手动加配额
     * - 配额刷新
     *
     * @param tokens 要增加的Token数量（正数）
     * @param model 模型名称
     */
    public void incrementQuota(int tokens, String model) {
        // 增加通用配额，不超过总配额上限
        quotaRemaining = quotaRemaining + tokens;
        if (quotaRemaining > quotaTotal) {
            quotaRemaining = quotaTotal;
        }
        // 增加模型独立配额
        if (modelQuotas != null && modelQuotas.containsKey(model)) {
            int currentModelQuota = modelQuotas.get(model);
            modelQuotas.put(model, Math.min(quotaTotal, currentModelQuota + tokens));
        }
    }
}