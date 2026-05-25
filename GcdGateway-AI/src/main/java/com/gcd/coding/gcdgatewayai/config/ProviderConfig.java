package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 提供商配置 - 配置AI模型提供商（如DeepSeek、OpenAI）的连接参数
 *
 * 功能说明：
 * 配置每个AI模型提供商的详细信息，包括API地址、密钥、超时设置、配额管理等。
 * 网关根据此配置连接AI提供商并调用其API。
 *
 * 配置项说明：
 * - name：提供商名称（如"deepseek"、"openai"）
 * - type：提供商类型，用于决定使用哪个Provider实现
 * - baseUrl：API地址（如https://api.deepseek.com）
 * - apiKey：API密钥
 * - protocol：协议类型（如"openai"）
 * - connectTimeout/requestTimeout：连接/请求超时（毫秒）
 * - maxRetries：最大重试次数
 * - maxConnections：最大连接数
 * - apiKeys：多个API密钥列表（用于负载均衡）
 * - apiKeyQuotas：每个APIKey的剩余配额
 *
 * 多Key负载均衡：
 * - 当配置多个apiKeys时，selectApiKeyByQuota()会根据配额权重选择Key
 * - 配额越高的Key被选中的概率越大
 *
 * 使用场景：
 * - AIModelProviderManager：根据type创建对应的Provider实例
 * - AIModelRouteFilter：根据providerName查找ProviderConfig
 * - OpenAIModelProvider/DeepSeekModelProvider：使用此配置调用API
 *
 * @see AIModelProviderManager 提供商管理器
 * @see OpenAIModelProvider OpenAI提供商
 * @see DeepSeekModelProvider DeepSeek提供商
 */
@Data
public class ProviderConfig {

    /** 提供商名称，用于在配置中标识和查找 */
    private String name;

    /** 提供商类型，决定使用哪个Provider实现：deepseek/openai */
    private String type = "openai";

    /** API基础地址，如 https://api.deepseek.com */
    private String baseUrl;

    /** API密钥，用于认证 */
    private String apiKey;

    /** 协议类型，如 "openai" */
    private String protocol = "openai";

    /** 是否支持流式响应，默认为true */
    private boolean supportsStreaming = true;

    /** 连接超时时间（毫秒），默认30000（30秒） */
    private int connectTimeout = 30000;

    /** 请求超时时间（毫秒），默认120000（2分钟） */
    private int requestTimeout = 120000;

    /** 最大重试次数，默认3 */
    private int maxRetries = 3;

    /** 最大连接数，默认50 */
    private int maxConnections = 50;

    /** 多个API密钥列表，用于负载均衡和配额管理 */
    private List<String> apiKeys;

    /** 每个APIKey的剩余配额映射 */
    private Map<String, AtomicInteger> apiKeyQuotas = new ConcurrentHashMap<>();

    /**
     * 获取指定APIKey的剩余配额
     *
     * @param apiKey APIKey字符串
     * @return 剩余配额，若不存在返回0
     */
    public int getRemainingQuota(String apiKey) {
        AtomicInteger quota = apiKeyQuotas.get(apiKey);
        return quota != null ? quota.get() : 0;
    }

    /**
     * 扣减指定APIKey的配额
     *
     * @param apiKey APIKey字符串
     * @param tokens 要扣除的Token数量
     */
    public void decrementQuota(String apiKey, int tokens) {
        AtomicInteger quota = apiKeyQuotas.computeIfAbsent(apiKey, k -> new AtomicInteger(Integer.MAX_VALUE));
        quota.addAndGet(-tokens);
    }

    /**
     * 增加指定APIKey的配额（修正用）
     *
     * @param apiKey APIKey字符串
     * @param tokens 要增加的Token数量
     */
    public void incrementQuota(String apiKey, int tokens) {
        AtomicInteger quota = apiKeyQuotas.computeIfAbsent(apiKey, k -> new AtomicInteger(Integer.MAX_VALUE));
        quota.addAndGet(tokens);
    }

    /**
     * 根据配额权重选择APIKey
     *
     * 选择算法：
     * 1. 汇总所有Key的配额得到总配额
     * 2. 生成0到总配额之间的随机数
     * 3. 遍历所有Key，累加配额直到超过随机数
     * 4. 返回当前Key
     *
     * 效果：配额越高的Key被选中的概率越大
     *
     * @return 选中的APIKey
     */
    public String selectApiKeyByQuota() {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return apiKey;
        }

        // 计算总配额
        int totalQuota = apiKeyQuotas.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

        if (totalQuota <= 0) {
            // 配额用尽时返回第一个Key
            return apiKeys.get(0);
        }

        // 生成随机数，选择Key
        int randomQuota = (int) (Math.random() * totalQuota);
        int cumulativeQuota = 0;

        for (String key : apiKeys) {
            AtomicInteger quota = apiKeyQuotas.get(key);
            int keyQuota = quota != null ? quota.get() : 0;
            cumulativeQuota += keyQuota;
            if (cumulativeQuota >= randomQuota) {
                return key;
            }
        }

        return apiKeys.get(0);
    }

}