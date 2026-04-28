package com.gcd.coding.gcdgatewayconfig.service;

import com.gcd.coding.gcdgatewayconfig.config.ConfigCenter;

/**
 * 配置中心接口
 */
public interface ConfigCenterProcessor {

    /**
     * 初始化配置中心配置
     */
    void init(ConfigCenter configCenter);

    /**
     * 订阅配置中心路由配置变更
     */
    void subscribeRoutesChange(RoutesChangeListener listener);

    /**
     * 订阅配置中心AI配置变更
     * @param listener AI配置变更监听器
     * @param aiDataId AI配置在Nacos中的dataId
     * @param aiGroup AI配置在Nacos中的group
     */
    void subscribeAIConfigChange(AIConfigChangeListener listener, String aiDataId, String aiGroup);

}
