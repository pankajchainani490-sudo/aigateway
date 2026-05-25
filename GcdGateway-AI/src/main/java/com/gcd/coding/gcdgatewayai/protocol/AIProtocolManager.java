package com.gcd.coding.gcdgatewayai.protocol;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI协议管理器 - 管理协议转换器并提供转换服务
 *
 * 功能说明：
 * 统一管理所有协议转换器，支持根据源协议和目标协议获取对应的转换器。
 * 采用单例模式，通过SPI机制自动加载所有实现了AIProtocolTransformer接口的转换器。
 *
 * 工作原理：
 * 1. 初始化时通过ServiceLoader加载所有转换器
 * 2. 转换器以"sourceProtocol->targetProtocol"为key存储
 * 3. 调用getTransformer()时根据source和target获取对应转换器
 *
 * 内置转换器：
 * - OpenAI -> Anthropic
 * - Anthropic -> OpenAI
 *
 * 使用场景：
 * - AIProtocolFilter：根据配置的协议进行请求/响应转换
 * - 跨Provider调用：如客户端用OpenAI格式调用Anthropic Claude
 *
 * @see AIProtocolTransformer 协议转换器接口
 * @see OpenAI2AnthropicTransformer OpenAI转Anthropic
 * @see Anthropic2OpenAITransformer Anthropic转OpenAI
 */
@Slf4j
public class AIProtocolManager {

    /** 单例实例 */
    private static final AIProtocolManager INSTANCE = new AIProtocolManager();

    /** 转换器映射表，Key格式：sourceProtocol->targetProtocol */
    private final Map<String, AIProtocolTransformer> transformerMap = new ConcurrentHashMap<>();

    /**
     * 私有构造函数
     *
     * 初始化步骤：
     * 1. 通过ServiceLoader加载所有实现了AIProtocolTransformer的转换器
     * 2. 将每个转换器以其source->target为key存入map
     * 3. 如果没有加载到任何转换器，手动注册内置的OpenAI和Anthropic转换器
     */
    private AIProtocolManager() {
        ServiceLoader<AIProtocolTransformer> loader = ServiceLoader.load(AIProtocolTransformer.class);
        for (AIProtocolTransformer transformer : loader) {
            String key = transformer.sourceProtocol() + "->" + transformer.targetProtocol();
            transformerMap.put(key, transformer);
            log.info("加载协议转换器: {}", key);
        }
        // 如果SPI没有加载到转换器，手动注册内置转换器
        if (transformerMap.isEmpty()) {
            OpenAI2AnthropicTransformer o2a = new OpenAI2AnthropicTransformer();
            transformerMap.put(o2a.sourceProtocol() + "->" + o2a.targetProtocol(), o2a);
            Anthropic2OpenAITransformer a2o = new Anthropic2OpenAITransformer();
            transformerMap.put(a2o.sourceProtocol() + "->" + a2o.targetProtocol(), a2o);
        }
    }

    /**
     * 获取单例实例
     *
     * @return AIProtocolManager单例
     */
    public static AIProtocolManager getInstance() {
        return INSTANCE;
    }

    /**
     * 根据源协议和目标协议获取转换器
     *
     * @param sourceProtocol 源协议，如 "openai"
     * @param targetProtocol 目标协议，如 "anthropic"
     * @return 对应的转换器，若不存在或相同协议则返回null
     */
    public AIProtocolTransformer getTransformer(String sourceProtocol, String targetProtocol) {
        // 同协议无需转换
        if (sourceProtocol.equals(targetProtocol)) return null;
        return transformerMap.get(sourceProtocol + "->" + targetProtocol);
    }

}