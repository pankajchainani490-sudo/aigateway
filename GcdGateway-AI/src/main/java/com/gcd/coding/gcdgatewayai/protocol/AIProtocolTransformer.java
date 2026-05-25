package com.gcd.coding.gcdgatewayai.protocol;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;

/**
 * AI协议转换器接口 - 定义不同AI协议之间的转换规则
 *
 * 功能说明：
 * 定义AI请求/响应在不同协议之间的转换方法。
 * 支持OpenAI、Anthropic等不同AI提供商协议的互转。
 *
 * 协议转换场景：
 * - 客户端使用OpenAI格式请求，但实际要调用Anthropic Claude
 * - AI提供商返回的响应格式需要转换为客户端期望的格式
 *
 * 实现类：
 * - OpenAI2AnthropicTransformer：OpenAI格式转Anthropic格式
 * - Anthropic2OpenAITransformer：Anthropic格式转OpenAI格式
 *
 * 使用场景：
 * - AIProtocolFilter：在请求发送前和响应返回后进行协议转换
 * - AIProtocolManager：根据source和target协议获取对应的转换器
 *
 * @see AIProtocolManager 协议管理器
 * @see OpenAI2AnthropicTransformer OpenAI转Anthropic转换器
 * @see Anthropic2OpenAITransformer Anthropic转OpenAI转换器
 */
public interface AIProtocolTransformer {

    /**
     * 返回源协议类型
     *
     * @return 源协议标识，如 "openai"、"anthropic"
     */
    String sourceProtocol();

    /**
     * 返回目标协议类型
     *
     * @return 目标协议标识，如 "openai"、"anthropic"
     */
    String targetProtocol();

    /**
     * 转换AI请求
     *
     * 将请求从源协议格式转换为目标协议格式
     *
     * @param source 源协议格式的AI请求
     * @return 目标协议格式的AI请求
     */
    AIRequest transformRequest(AIRequest source);

    /**
     * 转换AI响应
     *
     * 将响应从源协议格式转换为目标协议格式
     *
     * @param source 源协议格式的AI响应
     * @return 目标协议格式的AI响应
     */
    AIResponse transformResponse(AIResponse source);

}