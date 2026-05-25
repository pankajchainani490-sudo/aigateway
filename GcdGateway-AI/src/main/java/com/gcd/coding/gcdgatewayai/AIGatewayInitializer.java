package com.gcd.coding.gcdgatewayai;

import com.gcd.coding.gcdgatewayai.cache.AICacheManager;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfig;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfigManager;
import com.gcd.coding.gcdgatewayai.config.ProviderConfig;
import com.gcd.coding.gcdgatewayai.provider.AIModelProviderManager;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.AsyncHttpClient;

/**
 * AI网关初始化器 - 负责AI模块的启动初始化
 *
 * 功能说明：
 * 在网关启动时，根据配置初始化AI模块的各个组件，确保AI服务可以正常运行。
 * 这是AI模块的入口点，在Bootstrap阶段被调用。
 *
 * 初始化内容：
 * 1. 加载AI网关配置（AIGatewayConfig）
 * 2. 配置HTTP客户端（AsyncHttpClient）
 * 3. 注册AI模型提供商（DeepSeek、OpenAI等）
 * 4. 初始化三级语义缓存系统
 *
 * 使用场景：
 * 在GatewayBootstrap启动时调用AIGatewayInitializer.init(httpClient, aiConfig)
 * 完成AI模块的完整初始化流程
 */
@Slf4j
public class AIGatewayInitializer {

    /**
     * 初始化AI网关
     *
     * 初始化流程：
     * 1. 检查AI配置是否启用，未启用则跳过初始化
     * 2. 更新配置管理器中的配置（AIGatewayConfigManager单例）
     * 3. 配置HTTP客户端（用于后续AI Provider调用）
     * 4. 遍历配置中的Provider列表，注册每个AI模型提供商
     * 5. 如果缓存启用，初始化三级语义缓存系统
     * 6. 输出初始化完成日志，包含Provider数量、模型数量、缓存模式等信息
     *
     * @param httpClient 异步HTTP客户端，用于调用AI服务API
     * @param aiConfig AI网关配置对象，包含providers、models、cache、billing等配置
     */
    public static void init(AsyncHttpClient httpClient, AIGatewayConfig aiConfig) {
        // 第1步：检查AI配置是否启用
        if (aiConfig == null || !aiConfig.isEnabled()) {
            log.info("AI网关功能未启用");
            return;
        }

        // 第2步：更新配置管理器（单例模式）
        AIGatewayConfigManager.getInstance().updateConfig(aiConfig);

        // 第3步：配置HTTP客户端
        AIModelProviderManager.getInstance().setHttpClient(httpClient);

        // 第4步：注册所有AI模型提供商（DeepSeek、OpenAI等）
        if (aiConfig.getProviders() != null) {
            for (ProviderConfig providerConfig : aiConfig.getProviders()) {
                AIModelProviderManager.getInstance().registerProvider(providerConfig);
            }
        }

        // 第5步：初始化缓存系统（如启用）
        if (aiConfig.getCache().isEnabled()) {
            AICacheManager.getInstance().init(aiConfig.getCache());
        }

        // 第6步：输出初始化完成日志
        log.info("AI网关初始化完成: {} 个Provider, {} 个模型, 缓存模式: {}, 计费: {}",
                aiConfig.getProviders().size(),
                aiConfig.getModels().size(),
                aiConfig.getCache().getMode(),
                aiConfig.getBilling().isEnabled() ? "已开启" : "未开启");
    }

}