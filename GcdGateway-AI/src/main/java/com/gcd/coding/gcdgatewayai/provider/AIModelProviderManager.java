package com.gcd.coding.gcdgatewayai.provider;

import com.gcd.coding.gcdgatewayai.config.ProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.AsyncHttpClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI模型提供者管理器 - 管理所有AI模型提供者实例
 *
 * 功能说明：
 * 统一管理所有注册的AI模型提供者，支持根据名称获取对应的Provider实例。
 * 采用单例模式，确保整个AI模块使用同一套Provider实例。
 *
 * 提供者注册：
 * - 根据ProviderConfig的type字段决定创建哪种Provider
 * - type="deepseek"：创建DeepSeekModelProvider
 * - type="openai"或默认值：创建OpenAIModelProvider
 *
 * 使用场景：
 * - AIGatewayInitializer.init()：初始化时注册所有Provider
 * - AIModelRouteFilter：根据Provider名称获取Provider实例并调用
 *
 * 线程安全：
 * - 使用ConcurrentHashMap存储Provider实例
 * - 单例模式保证全局唯一实例
 *
 * @see AIModelProvider 模型提供者接口
 * @see OpenAIModelProvider OpenAI提供者
 * @see DeepSeekModelProvider DeepSeek提供者
 * @see ProviderConfig 提供者配置
 */
@Slf4j
public class AIModelProviderManager {

    /** 单例实例 */
    private static final AIModelProviderManager INSTANCE = new AIModelProviderManager();

    /** 提供者映射表，Key: 提供商名称，Value: Provider实例 */
    private final Map<String, AIModelProvider> providerMap = new ConcurrentHashMap<>();

    /** HTTP客户端引用，用于Provider调用AI API */
    private AsyncHttpClient httpClient;

    /**
     * 私有构造函数
     */
    private AIModelProviderManager() {
    }

    /**
     * 获取单例实例
     *
     * @return AIModelProviderManager单例
     */
    public static AIModelProviderManager getInstance() {
        return INSTANCE;
    }

    /**
     * 配置HTTP客户端
     *
     * @param httpClient 异步HTTP客户端
     */
    public void setHttpClient(AsyncHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 注册AI模型提供者
     *
     * 根据config的type字段创建对应类型的Provider实例
     *
     * @param config 提供者配置
     */
    public void registerProvider(ProviderConfig config) {
        AIModelProvider provider;
        // 根据type创建对应的Provider
        if ("deepseek".equalsIgnoreCase(config.getType())) {
            provider = new DeepSeekModelProvider(config);
        } else {
            provider = new OpenAIModelProvider(config);
        }
        // 设置HTTP客户端
        if (provider instanceof OpenAIModelProvider) {
            ((OpenAIModelProvider) provider).setHttpClient(httpClient);
        }
        // 存入映射表
        providerMap.put(config.getName(), provider);
        log.info("注册AI提供商: {} -> {} ({})", config.getName(), config.getType(), config.getBaseUrl());
    }

    /**
     * 根据名称获取Provider实例
     *
     * @param name 提供商名称
     * @return Provider实例，若不存在返回null
     */
    public AIModelProvider getProvider(String name) {
        return providerMap.get(name);
    }

    /**
     * 获取所有Provider实例
     *
     * @return 所有Provider的映射表
     */
    public Map<String, AIModelProvider> getAllProviders() {
        return providerMap;
    }

    /**
     * 清空所有Provider
     *
     * 通常在配置重新加载时调用
     */
    public void clear() {
        providerMap.clear();
    }

}