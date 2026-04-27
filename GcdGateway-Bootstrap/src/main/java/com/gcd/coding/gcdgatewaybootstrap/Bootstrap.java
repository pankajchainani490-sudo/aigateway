package com.gcd.coding.gcdgatewaybootstrap;

import com.gcd.coding.gcdgatewayai.AIGatewayInitializer;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfig;
import com.gcd.coding.gcdgatewayconfig.config.Config;
import com.gcd.coding.gcdgatewayconfig.loader.ConfigLoader;
import com.gcd.coding.gcdgatewayconfig.manager.DynamicConfigManager;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
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

    private void initAIGateway() {
        try {
            AIGatewayConfig aiConfig = ConfigUtil.loadConfigFromYaml("gateway.yaml", AIGatewayConfig.class, "gcd.gateway.ai");
            if (aiConfig != null && aiConfig.isEnabled()) {
                AIGatewayInitializer.init(
                        HttpClient.getInstance().getAsyncHttpClient(),
                        aiConfig);
            } else {
                log.info("AI网关功能未配置或未启用，跳过AI初始化");
            }
        } catch (Exception e) {
            log.warn("AI网关配置加载失败，跳过AI初始化: {}", e.getMessage());
        }
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
