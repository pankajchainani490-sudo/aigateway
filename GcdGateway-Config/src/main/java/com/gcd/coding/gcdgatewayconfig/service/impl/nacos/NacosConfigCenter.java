package com.gcd.coding.gcdgatewayconfig.service.impl.nacos;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gcd.coding.gcdgatewayconfig.config.ConfigCenter;
import com.gcd.coding.gcdgatewayconfig.config.lib.nacos.NacosConfig;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewayconfig.service.AIConfigChangeListener;
import com.gcd.coding.gcdgatewayconfig.service.ConfigCenterProcessor;
import com.gcd.coding.gcdgatewayconfig.service.RoutesChangeListener;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class NacosConfigCenter implements ConfigCenterProcessor {

    /**
     * 配置
     */
    private ConfigCenter configCenter;

    /**
     * Nacos提供的与配置中心进行交互的接口
     */
    private ConfigService configService;

    /**
     * 是否完成初始化
     */
    private final AtomicBoolean init = new AtomicBoolean(false);


    @SneakyThrows(NacosException.class)
    public void init(ConfigCenter configCenter) {
        this.configCenter = configCenter;
        if (!configCenter.isEnabled() || !init.compareAndSet(false, true)) {
            return;
        }
        this.configService = NacosFactory.createConfigService(buildProperties(configCenter));
    }

    @SneakyThrows(NacosException.class)
    public void subscribeRoutesChange(RoutesChangeListener listener) {
        if (!configCenter.isEnabled() || !init.get()) {
            return;
        }
        NacosConfig nacos = configCenter.getNacos();
        String configJson = configService.getConfig(nacos.getDataId(), nacos.getGroup(), nacos.getTimeout());
        log.info("从Nacos加载路由配置: \n{}", configJson);
        List<RouteDefinition> routes = JSON.parseObject(configJson).getJSONArray("routes").toJavaList(RouteDefinition.class);
        listener.onRoutesChange(routes);

        configService.addListener(nacos.getDataId(), nacos.getGroup(), new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("Nacos路由配置变更通知: {}", configInfo);
                List<RouteDefinition> routes = JSON.parseObject(configInfo).getJSONArray("routes").toJavaList(RouteDefinition.class);
                listener.onRoutesChange(routes);
            }
        });
    }

    @Override
    public void subscribeAIConfigChange(AIConfigChangeListener listener, String aiDataId, String aiGroup) {
        if (!configCenter.isEnabled() || !init.get()) {
            log.warn("Nacos配置中心未启用，跳过AI配置订阅");
            return;
        }
        // 先获取一次当前配置
        try {
            String configJson = configService.getConfig(aiDataId, aiGroup, 5000);
            if (configJson != null && !configJson.isEmpty()) {
                log.info("从Nacos加载AI配置: \n{}", configJson);
                listener.onAIConfigChange(configJson);
            }
        } catch (NacosException e) {
            log.error("从Nacos获取AI配置失败: {}", e.getMessage());
        }

        // 添加监听器，监听配置变更
        try {
            configService.addListener(aiDataId, aiGroup, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("Nacos AI配置变更通知: {}", configInfo);
                    listener.onAIConfigChange(configInfo);
                }
            });
        } catch (NacosException e) {
            log.error("添加AI配置监听器失败: {}", e.getMessage());
        }
    }

    private Properties buildProperties(ConfigCenter configCenter) {
        ObjectMapper mapper = new ObjectMapper();
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, configCenter.getAddress());
        Map map = mapper.convertValue(configCenter.getNacos(), Map.class);
        if (map == null || map.isEmpty()) return properties;
        properties.putAll(map);
        return properties;
    }

}
