package com.gcd.coding.gcdgatewayai.config;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

/**
 * AI网关配置管理器 - 管理AI配置的加载和动态更新
 *
 * 功能说明：
 * 作为AI配置的单一数据源，负责配置的加载、存储和动态更新。
 * 采用单例模式，确保整个AI模块使用同一份配置。
 *
 * 配置管理：
 * 1. 配置加载：支持从YAML文件或InputStream加载配置
 * 2. 配置存储：持有AIGatewayConfig单例
 * 3. 动态更新：支持运行时更新配置（从Nacos配置中心订阅）
 *
 * 使用场景：
 * - AIGatewayInitializer.init()：初始化时加载配置并更新到管理器
 * - 各过滤器：通过AIGatewayConfigManager.getInstance().getConfig()获取配置
 * - 配置变更监听：当Nacos配置变更时，调用updateConfig()更新配置
 *
 * 线程安全：
 * - 单例模式保证全局唯一实例
 * - 配置更新时直接替换引用，保证原子性
 *
 * @see AIGatewayConfig AI网关配置类
 */
@Slf4j
public class AIGatewayConfigManager {

    /** 单例实例 */
    private static final AIGatewayConfigManager INSTANCE = new AIGatewayConfigManager();

    /** AI网关配置持有者 */
    private AIGatewayConfig config;

    /**
     * 私有构造函数
     * 外部无法直接创建实例，必须通过getInstance()获取
     */
    private AIGatewayConfigManager() {
    }

    /**
     * 获取单例实例
     *
     * @return AIGatewayConfigManager单例
     */
    public static AIGatewayConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取当前配置
     *
     * 如果配置未加载，返回默认配置（enabled=false）
     *
     * @return 当前AI网关配置
     */
    public AIGatewayConfig getConfig() {
        if (config == null) {
            config = new AIGatewayConfig();
        }
        return config;
    }

    /**
     * 从InputStream加载配置
     *
     * 使用Jackson的YAMLFactory解析YAML格式配置
     *
     * @param inputStream YAML配置文件输入流
     */
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

    /**
     * 动态更新配置
     *
     * 当Nacos配置中心发生变更时，调用此方法更新配置
     * 更新后，所有过滤器获取到的配置将是最新版本
     *
     * @param newConfig 新的AI网关配置
     */
    public void updateConfig(AIGatewayConfig newConfig) {
        this.config = newConfig;
        log.info("AI网关配置已动态更新");
    }

}