package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * AI网关配置 - AI模块的顶级配置类
 *
 * 功能说明：
 * 作为AI模块配置的根对象，包含了AI网关运行所需的全部配置信息。
 * 采用@ConfigurationProperties风格设计，便于从YAML/JSON加载配置。
 *
 * 配置内容：
 * - enabled：AI模块总开关
 * - providers：AI提供商列表（DeepSeek、OpenAI等）
 * - models：模型配置列表（每个模型的定价、限额等）
 * - cache：缓存配置（模式、TTL、容量等）
 * - billing：计费配置（开关、货币、日志等）
 * - token：Token限流配置（分钟限额、日限额等）
 *
 * 使用场景：
 * - AIGatewayInitializer：初始化时从此配置读取AI模块配置
 * - AIGatewayConfigManager：单例持有此配置，供各过滤器访问
 * - AISemanticCacheFilter：根据cache配置决定是否启用缓存
 * - AITokenFilter：根据token配置进行Token限流
 *
 * @see AIGatewayConfigManager 配置管理器（单例）
 * @see ProviderConfig 提供商配置
 * @see ModelConfig 模型配置
 * @see CacheConfig 缓存配置
 * @see BillingConfig 计费配置
 * @see TokenConfig Token限流配置
 */
@Data
public class AIGatewayConfig {

    /** AI模块总开关，默认为true（启用） */
    private boolean enabled = true;

    /** AI提供商列表，如DeepSeek、OpenAI等 */
    private List<ProviderConfig> providers = new ArrayList<>();

    /** 模型配置列表，包含每个模型的定价、限额等信息 */
    private List<ModelConfig> models = new ArrayList<>();

    /** 缓存配置，包含缓存模式、TTL、容量、相似度阈值等 */
    private CacheConfig cache = new CacheConfig();

    /** 计费配置，包含开关、货币、日志等 */
    private BillingConfig billing = new BillingConfig();

    /** Token限流配置，包含分钟限额、日限额等 */
    private TokenConfig token = new TokenConfig();

}