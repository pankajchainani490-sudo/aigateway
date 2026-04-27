package com.gcd.coding.gcdgatewayai;

import com.gcd.coding.gcdgatewayai.cache.AICacheManager;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfig;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfigManager;
import com.gcd.coding.gcdgatewayai.config.ProviderConfig;
import com.gcd.coding.gcdgatewayai.provider.AIModelProviderManager;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.AsyncHttpClient;

@Slf4j
public class AIGatewayInitializer {

    public static void init(AsyncHttpClient httpClient, AIGatewayConfig aiConfig) {
        if (aiConfig == null || !aiConfig.isEnabled()) {
            log.info("AI网关功能未启用");
            return;
        }

        AIGatewayConfigManager.getInstance().updateConfig(aiConfig);

        AIModelProviderManager.getInstance().setHttpClient(httpClient);

        if (aiConfig.getProviders() != null) {
            for (ProviderConfig providerConfig : aiConfig.getProviders()) {
                AIModelProviderManager.getInstance().registerProvider(providerConfig);
            }
        }

        if (aiConfig.getCache().isEnabled()) {
            AICacheManager.getInstance().init(aiConfig.getCache());
        }

        log.info("AI网关初始化完成: {} 个Provider, {} 个模型, 缓存模式: {}, 计费: {}",
                aiConfig.getProviders().size(),
                aiConfig.getModels().size(),
                aiConfig.getCache().getMode(),
                aiConfig.getBilling().isEnabled() ? "已开启" : "未开启");
    }

}
