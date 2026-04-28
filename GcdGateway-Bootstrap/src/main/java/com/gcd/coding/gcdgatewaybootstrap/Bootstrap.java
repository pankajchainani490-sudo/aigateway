package com.gcd.coding.gcdgatewaybootstrap;

import com.alibaba.fastjson.JSON;
import com.gcd.coding.gcdgatewayai.AIGatewayInitializer;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfig;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfigManager;
import com.gcd.coding.gcdgatewayconfig.config.Config;
import com.gcd.coding.gcdgatewayconfig.config.ConfigCenter;
import com.gcd.coding.gcdgatewayconfig.config.lib.nacos.NacosConfig;
import com.gcd.coding.gcdgatewayconfig.loader.ConfigLoader;
import com.gcd.coding.gcdgatewayconfig.manager.DynamicConfigManager;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewayconfig.service.AIConfigChangeListener;
import com.gcd.coding.gcdgatewayconfig.service.ConfigCenterProcessor;
import com.gcd.coding.gcdgatewayconfig.util.ConfigUtil;
import com.gcd.coding.gcdgatewaycore.config.Container;
import com.gcd.coding.gcdgatewaycore.http.HttpClient;
import com.gcd.coding.gcdgatewayregister.service.RegisterCenterProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.ServiceLoader;

@Slf4j
public class Bootstrap {

    private Config config;

    private Container container;

    public static void run(String[] args) {
        new Bootstrap().start(args);
    }

    public void start(String[] args) {
        log.info("gateway bootstrap start...");

        config = ConfigLoader.load(args);
        log.info("gateway bootstrap load config: {}", config);

        initConfigCenter();

        initContainer();
        container.start();

        initAIGateway();

        initRegisterCenter();

        registerGracefullyShutdown();
    }

    /**
     * 初始化AI网关配置
     * 配置优先级：Nacos > 本地yaml
     * 流程：
     * 1. 先加载本地yaml作为基础配置
     * 2. 再从Nacos订阅AI配置（会覆盖本地配置）
     * 3. 初始化AI网关
     */
    private void initAIGateway() {
        // 1. 先从本地yaml加载AI配置作为基础配置
        try {
            AIGatewayConfig localAiConfig = ConfigUtil.loadConfigFromYaml("gateway.yaml", AIGatewayConfig.class, "gcd.gateway.ai");
            if (localAiConfig != null && localAiConfig.isEnabled()) {
                // 预加载本地配置，确保在Nacos不可用时也有配置可用
                AIGatewayConfigManager.getInstance().updateConfig(localAiConfig);
                log.info("AI网关本地配置已加载: {} providers, {} models",
                        localAiConfig.getProviders().size(), localAiConfig.getModels().size());
            }
        } catch (Exception e) {
            log.warn("AI网关本地配置加载失败: {}", e.getMessage());
        }

        // 2. 尝试从Nacos订阅AI配置（会覆盖本地配置）
        initAIConfigFromNacos();

        // 3. 初始化AI网关
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (aiConfig != null && aiConfig.isEnabled()) {
            AIGatewayInitializer.init(HttpClient.getInstance().getAsyncHttpClient(), aiConfig);
        } else {
            log.info("AI网关功能未启用，跳过AI初始化");
        }
    }

    /**
     * 从Nacos配置中心订阅AI配置
     * Nacos配置优先级高于本地yaml配置
     */
    private void initAIConfigFromNacos() {
        ConfigCenter configCenter = config.getConfigCenter();
        if (configCenter == null || !configCenter.isEnabled()) {
            log.info("Nacos配置中心未启用，AI配置使用本地yaml");
            return;
        }

        ConfigCenterProcessor configCenterProcessor = ServiceLoader.load(ConfigCenterProcessor.class).findFirst().orElse(null);
        if (configCenterProcessor == null) {
            log.warn("未找到ConfigCenterProcessor实现，AI配置使用本地yaml");
            return;
        }

        NacosConfig nacos = configCenter.getNacos();
        String aiDataId = nacos.getAiDataId() != null ? nacos.getAiDataId() : "gcd-ai-gateway";
        String aiGroup = nacos.getAiGroup() != null ? nacos.getAiGroup() : "DEFAULT_GROUP";

        log.info("尝试从Nacos订阅AI配置, dataId={}, group={}", aiDataId, aiGroup);

        configCenterProcessor.subscribeAIConfigChange(new AIConfigChangeListener() {
            @Override
            public void onAIConfigChange(String configInfo) {
                try {
                    AIGatewayConfig newConfig = JSON.parseObject(configInfo, AIGatewayConfig.class);
                    if (newConfig != null && newConfig.isEnabled()) {
                        AIGatewayConfigManager.getInstance().updateConfig(newConfig);
                        // 重新初始化AI网关
                        AIGatewayInitializer.init(HttpClient.getInstance().getAsyncHttpClient(), newConfig);
                        log.info("AI配置已从Nacos更新并重新初始化: {} providers, {} models",
                                newConfig.getProviders().size(), newConfig.getModels().size());
                    }
                } catch (Exception e) {
                    log.error("解析Nacos AI配置失败: {}", e.getMessage());
                }
            }
        }, aiDataId, aiGroup);
    }

    private void initConfigCenter() {
        ConfigCenterProcessor configCenterProcessor = ServiceLoader.load(ConfigCenterProcessor.class).findFirst().orElseThrow(() -> {
            log.error("not found ConfigCenter impl");
            return new RuntimeException("not found ConfigCenter impl");
        });
        configCenterProcessor.init(config.getConfigCenter());
        configCenterProcessor.subscribeRoutesChange(newRoutes -> {
            DynamicConfigManager.getInstance().updateRoutes(newRoutes, true);
            for (RouteDefinition newRoute : newRoutes) {
                DynamicConfigManager.getInstance().changeRoute(newRoute);
            }
        });
    }

    private void initContainer() {
        container = new Container(config);
        // 初始化路由配置到DynamicConfigManager
        if (config.getRoutes() != null && !config.getRoutes().isEmpty()) {
            DynamicConfigManager.getInstance().updateRoutes(config.getRoutes(), true);
            log.info("路由配置已加载: {} 个路由", config.getRoutes().size());
        }
    }

    private void initRegisterCenter() {
        RegisterCenterProcessor registerCenterProcessor = ServiceLoader.load(RegisterCenterProcessor.class).findFirst().orElseThrow(() -> {
            log.error("not found RegisterCenter impl");
            return new RuntimeException("not found RegisterCenter impl");
        });
        registerCenterProcessor.init(config);
        registerCenterProcessor.subscribeServiceChange(((serviceDefinition, newInstances) -> {
            DynamicConfigManager.getInstance().updateService(serviceDefinition);
            DynamicConfigManager.getInstance().updateInstances(serviceDefinition, newInstances);
        }));
    }

    private void registerGracefullyShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            container.shutdown();
        }));
    }

}
