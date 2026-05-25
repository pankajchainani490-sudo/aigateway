package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型配置 - 配置每个AI模型的参数和定价
 *
 * 功能说明：
 * 配置每个AI模型的详细信息，包括所属提供商、API模型ID、定价、Token限制等。
 * 网关根据此配置进行模型路由和计费。
 *
 * 配置项说明：
 * - modelName：网关识别的模型名称（如"deepseek-chat"）
 * - providerName：所属提供商名称（如"deepseek"）
 * - providerModelId：提供商实际的模型ID（用于API调用）
 * - inputPricePer1KTokens：输入价格（元/千Token）
 * - outputPricePer1KTokens：输出价格（元/千Token）
 * - cacheHitDiscount：缓存命中折扣（0.0-1.0）
 * - maxInputTokens/maxOutputTokens：最大输入/输出Token数
 * - supportsStreaming：是否支持流式响应
 * - supportsVision：是否支持视觉（图片输入）
 * - weight：负载均衡权重
 *
 * 使用场景：
 * - AIModelRouteFilter：根据modelName查找ModelConfig，确定路由目标
 * - AIBillingFilter：根据定价计算费用
 * - QuotaWeightedLoadBalanceStrategy：根据weight分配负载
 *
 * @see ProviderConfig 提供商配置
 * @see AIModelProviderManager 模型提供者管理器
 */
@Data
@EqualsAndHashCode
public class ModelConfig {

    /** 网关识别的模型名称，客户端请求时使用此名称 */
    private String modelName;

    /** 所属提供商名称，用于查找ProviderConfig */
    private String providerName;

    /** 提供商实际的模型ID，用于API调用 */
    private String providerModelId;

    /** 输入价格（元/千Token），默认0.001 */
    private double inputPricePer1KTokens = 0.001;

    /** 输出价格（元/千Token），默认0.002 */
    private double outputPricePer1KTokens = 0.002;

    /** 缓存命中折扣（0.0-1.0），默认1.0（无折扣） */
    private double cacheHitDiscount = 1.0;

    /** 最大输入Token数，默认128000 */
    private int maxInputTokens = 128000;

    /** 最大输出Token数，默认4096 */
    private int maxOutputTokens = 4096;

    /** 是否支持流式响应，默认true */
    private boolean supportsStreaming = true;

    /** 是否支持视觉（图片输入），默认false */
    private boolean supportsVision = false;

    /** 负载均衡权重，默认100 */
    private int weight = 100;

}