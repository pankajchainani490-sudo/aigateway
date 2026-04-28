package com.gcd.coding.gcdgatewayai.provider;

import com.gcd.coding.gcdgatewayai.config.ProviderConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * DeepSeek模型提供者 - 实现DeepSeek API调用
 *
 * DeepSeek是一个国产大模型提供商，其API格式与OpenAI兼容
 * 因此DeepSeekModelProvider继承OpenAIModelProvider，复用OpenAI协议实现
 *
 * 主要功能：
 * 1. chat() - 异步非流式聊天请求
 * 2. chatStream() - 异步流式聊天请求（SSE）
 * 3. 支持Function Calling/Tools功能
 *
 * 使用方法：
 * 在gateway.yaml中配置provider.type为"deepseek"即可自动使用此Provider
 *
 * @see OpenAIModelProvider 父类，封装了OpenAI协议的具体实现
 */
@Slf4j
public class DeepSeekModelProvider extends OpenAIModelProvider {

    /**
     * 构造函数 - 创建DeepSeek Provider实例
     *
     * @param config Provider配置信息，包含：
     *               - baseUrl: API地址 (https://api.deepseek.com)
     *               - apiKey: API密钥
     *               - connectTimeout: 连接超时
     *               - requestTimeout: 请求超时
     *               - maxRetries: 最大重试次数
     */
    public DeepSeekModelProvider(ProviderConfig config) {
        // 调用父类构造函数，传入配置
        super(config);
        log.info("DeepSeekModelProvider创建完成, baseUrl={}", config != null ? config.getBaseUrl() : "null");
    }

    /**
     * 获取Provider名称
     * 继承父类实现，返回配置中的name字段
     */
    @Override
    public String providerName() {
        return super.providerName();
    }

    /**
     * 获取协议类型
     * DeepSeek使用OpenAI兼容协议
     *
     * @return "openai" - 表示使用OpenAI API格式
     */
    @Override
    public String protocol() {
        return "openai";
    }
}
