package com.gcd.coding.gcdgatewayai.metadata;

// Lombok自动生成getter/setter/toString等方法
import lombok.Data;

// Java Map接口 - 存储各模型的配额信息
import java.util.Map;

/**
 * API-Key元数据类
 *
 * 功能说明：
 * 存储与API-Key关联的所有用户和配额信息，供过滤器链中的各个组件使用。
 * 该对象会被存入GatewayContext，在AI模块的各个过滤器间共享。
 *
 * 使用场景：
 * - AuthFilter：验证用户身份和套餐权限
 * - AITokenFilter：检查用户剩余配额
 * - QuotaWeightedLoadBalanceStrategy：根据配额分配负载权重
 * - AITokenPostCorrectionFilter：修正Token配额
 *
 * 数据来源：
 * - 实际项目中应从用户中心/数据库获取
 * - 当前实现为模拟数据派生
 */
@Data  // Lombok注解，自动生成getter/setter/toString/equals/hashCode等方法
public class ApiKeyMetadata {

    // ========== 用户标识字段 ==========

    /**
     * 用户唯一标识符
     * 格式示例：user_12345678
     * 用于日志追踪、配额统计、计费等
     */
    private String userId;

    /**
     * API-Key本身
     * 格式示例：sk-pro-xxxx1234xxxx5678
     * 用于标识和验证请求来源
     */
    private String apiKey;

    // ========== 配额相关字段 ==========

    /**
     * 用户剩余配额（Token数量）
     * 单位：Token数量（非请求次数）
     * 扣减规则：每次请求根据输入+输出Token数扣减
     * 警戒线：当剩余配额低于阈值时应提醒用户续费
     */
    private int quotaRemaining;

    /**
     * 用户总配额（周期内总额度）
     * 通常为日配额或月配额
     * 示例：日配额10000Tokens
     */
    private int quotaTotal;

    // ========== 套餐相关字段 ==========

    /**
     * 套餐类型
     * 可选值：
     * - "free": 免费版，有基础配额限制
     * - "pro": 专业版，有较高配额限制
     * - "enterprise": 企业版，有最高配额限制
     *
     * 用于区分不同套餐的功能权限和配额限制
     */
    private String planType;

    /**
     * API-Key过期时间戳
     * 格式：Unix时间戳（毫秒）
     * - 0表示永不过期
     * - 大于0表示在指定时间后过期
     *
     * 检查逻辑：System.currentTimeMillis() > expiresAt 时视为过期
     */
    private long expiresAt;

    // ========== 模型配额字段 ==========

    /**
     * 各模型的独立配额映射
     *
     * 用途：某些高级套餐支持对特定模型设置独立配额
     * Key: 模型名称，如 "gpt-4", "claude-3"
     * Value: 该模型的剩余配额
     *
     * 示例：
     * {
     *   "gpt-4": 1000,
     *   "gpt-3.5-turbo": 5000,
     *   "claude-3": 800
     * }
     *
     * 注意：若某模型不在此映射中，则使用通用配额(quotaRemaining)
     */
    private Map<String, Integer> modelQuotas;

    // ========== 业务判断方法 ==========

    /**
     * 判断API-Key是否已过期
     *
     * 判断逻辑：
     * 1. 如果expiresAt <= 0，表示永不过期，返回false
     * 2. 否则比较当前时间与过期时间，返回比较结果
     *
     * 使用场景：
     * - AuthFilter在认证时调用，过期Key返回403
     * - 定时任务清理过期会话
     *
     * @return true表示已过期，false表示未过期或永不过期
     */
    public boolean isExpired() {
        // expiresAt大于0且当前时间已超过过期时间，则过期
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    /**
     * 检查指定模型是否还有可用配额
     *
     * 判断逻辑（优先级递减）：
     * 1. 若quotaRemaining <= 0，直接返回false
     * 2. 若modelQuotas包含该模型，检查模型配额 > 0
     * 3. 若modelQuotas不包含该模型，使用通用配额逻辑
     *
     * 使用场景：
     * - AITokenFilter在处理请求前检查
     * - LoadBalanceFilter在路由前检查
     *
     * @param model 模型名称（如 "gpt-4"）
     * @return true表示有可用配额，false表示配额已用尽
     */
    public boolean hasQuota(String model) {
        // 第1优先级：检查通用配额是否用尽
        if (quotaRemaining <= 0) {
            return false;
        }

        // 第2优先级：检查特定模型的配额（如果有设置）
        if (modelQuotas != null && modelQuotas.containsKey(model)) {
            return modelQuotas.get(model) > 0;
        }

        // 第3优先级：模型未设置独立配额，使用通用配额
        return true;
    }

    // ========== 配额操作方法 ==========

    /**
     * 扣减配额
     *
     * 扣减规则：
     * 1. 从通用配额中扣除指定Token数（最低为0）
     * 2. 若存在模型独立配额，同时从模型配额中扣除
     *
     * 注意：此方法直接修改对象状态，非原子操作
     * 分布式环境下建议使用Redis Lua脚本保证原子性
     *
     * @param tokens 要扣除的Token数量（正数）
     * @param model 模型名称，用于扣减模型独立配额
     */
    public void decrementQuota(int tokens, String model) {
        // 扣减通用配额，确保不低于0
        if (quotaRemaining > 0) {
            quotaRemaining = Math.max(0, quotaRemaining - tokens);
        }

        // 扣减模型独立配额（如果有）
        if (modelQuotas != null && modelQuotas.containsKey(model)) {
            int currentModelQuota = modelQuotas.get(model);
            modelQuotas.put(model, Math.max(0, currentModelQuota - tokens));
        }
    }

    /**
     * 增加配额（修正用）
     *
     * 使用场景：
     * - Token后置修正：实际Token数与预估值差异，多退少补
     * - 管理员手动加配额
     * - 配额刷新（周期性重置）
     *
     * 增加规则：
     * 1. 通用配额增加，但不超过总配额quotaTotal
     * 2. 模型配额增加，但不超过通用总配额
     *
     * @param tokens 要增加的Token数量（正数）
     * @param model 模型名称，用于增加模型独立配额
     */
    public void incrementQuota(int tokens, String model) {
        // 增加通用配额，不超过总配额上限
        quotaRemaining = quotaRemaining + tokens;
        if (quotaRemaining > quotaTotal) {
            quotaRemaining = quotaTotal;
        }

        // 增加模型独立配额（如果有），不超过通用总配额
        if (modelQuotas != null && modelQuotas.containsKey(model)) {
            int currentModelQuota = modelQuotas.get(model);
            modelQuotas.put(model, Math.min(quotaTotal, currentModelQuota + tokens));
        }
    }
}
