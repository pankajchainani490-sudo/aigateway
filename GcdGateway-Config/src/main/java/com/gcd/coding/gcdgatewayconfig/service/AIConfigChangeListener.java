package com.gcd.coding.gcdgatewayconfig.service;

/**
 * AI配置变更监听器
 * 当Nacos配置中心的AI配置发生变更时，会调用此监听器
 */
public interface AIConfigChangeListener {

    /**
     * AI配置变更时调用此方法
     * @param configInfo 新的AI配置JSON字符串
     */
    void onAIConfigChange(String configInfo);
}
