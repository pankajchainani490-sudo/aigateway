package com.gcd.coding.gcdgatewayai.config;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

@Slf4j
public class AIGatewayConfigManager {

    private static final AIGatewayConfigManager INSTANCE = new AIGatewayConfigManager();

    private AIGatewayConfig config;

    private AIGatewayConfigManager() {
    }

    public static AIGatewayConfigManager getInstance() {
        return INSTANCE;
    }

    public AIGatewayConfig getConfig() {
        if (config == null) {
            config = new AIGatewayConfig();
        }
        return config;
    }

    public void loadConfig(InputStream inputStream) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            config = mapper.readValue(inputStream, AIGatewayConfig.class);
            log.info("AI网关配置加载成功: {} providers, {} models", 
                    config.getProviders().size(), config.getModels().size());
        } catch (Exception e) {
            log.error("AI网关配置加载失败，使用默认配置: {}", e.getMessage());
            config = new AIGatewayConfig();
        }
    }

    public void updateConfig(AIGatewayConfig newConfig) {
        this.config = newConfig;
        log.info("AI网关配置已动态更新");
    }

}
