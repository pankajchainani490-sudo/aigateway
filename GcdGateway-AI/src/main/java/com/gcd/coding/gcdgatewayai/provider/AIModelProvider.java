package com.gcd.coding.gcdgatewayai.provider;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.CompletableFuture;

/**
 * AI模型提供者接口 - 定义AI模型调用的标准接口
 *
 * 功能说明：
 * 定义AI模型调用的基本操作，所有AI提供商（如DeepSeek、OpenAI）
 * 都必须实现此接口。这是AI模块调用层的基础接口。
 *
 * 实现类：
 * - OpenAIModelProvider：OpenAI模型提供者
 * - DeepSeekModelProvider：DeepSeek模型提供者（继承OpenAIModelProvider）
 *
 * 两种调用方式：
 * 1. chat() - 非流式调用，返回CompletableFuture<AIResponse>
 * 2. chatStream() - 流式调用，通过ChannelHandlerContext直接写入SSE
 *
 * 使用场景：
 * - AIModelRouteFilter：根据配置调用对应Provider的chat()或chatStream()
 * - AIModelProviderManager：管理所有注册的Provider实例
 *
 * @see OpenAIModelProvider OpenAI模型提供者
 * @see DeepSeekModelProvider DeepSeek模型提供者
 * @see AIModelProviderManager 提供者管理器
 */
public interface AIModelProvider {

    /**
     * 获取Provider名称
     *
     * @return 提供商名称，如 "deepseek"、"openai"
     */
    String providerName();

    /**
     * 获取协议类型
     *
     * @return 协议类型，如 "openai"
     */
    String protocol();

    /**
     * 是否支持流式响应
     *
     * @return true表示支持流式响应
     */
    boolean supportsStreaming();

    /**
     * 异步非流式聊天请求
     *
     * 调用AI模型并等待完整响应返回
     *
     * @param request AI请求对象
     * @param providerModelId 提供商实际的模型ID
     * @return 包含AI响应的CompletableFuture
     */
    CompletableFuture<AIResponse> chat(AIRequest request, String providerModelId);

    /**
     * 异步流式聊天请求
     *
     * 调用AI模型并通过SSE逐步返回响应内容
     *
     * @param request AI请求对象
     * @param providerModelId 提供商实际的模型ID
     * @param nettyCtx Netty通道上下文，用于直接写入SSE数据
     */
    void chatStream(AIRequest request, String providerModelId, ChannelHandlerContext nettyCtx);

}