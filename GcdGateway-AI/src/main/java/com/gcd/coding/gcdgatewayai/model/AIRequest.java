package com.gcd.coding.gcdgatewayai.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * AI请求模型 - 封装发送给AI模型的请求参数
 *
 * 功能说明：
 * 遵循OpenAI Chat Completions API格式，封装AI对话请求的所有参数。
 * 支持多轮对话、流式响应、功能调用等特性。
 *
 * 主要配置项：
 * - model：模型名称（如"deepseek-chat"、"gpt-4"）
 * - messages：对话消息列表
 * - temperature：输出随机性控制
 * - maxTokens：最大输出Token数
 * - stream：是否使用流式响应
 *
 * 支持的AI Provider：
 * - DeepSeek（通过DeepSeekModelProvider）
 * - OpenAI（通过OpenAIModelProvider）
 * - Anthropic（通过协议转换器支持）
 *
 * @see ChatMessage 聊天消息模型
 * @see AIResponse AI响应模型
 * @see AIModelProvider 模型提供者接口
 */
@Data
public class AIRequest {

    /**
     * 模型名称
     *
     * 说明：客户端请求时指定的模型名称
     * 示例: "deepseek-chat", "gpt-4", "claude-3-opus"
     *
     * 网关会根据此名称查找对应的ModelConfig进行路由：
     * 1. 在AIGatewayConfig.models中查找匹配的modelName
     * 2. 根据ModelConfig.providerName找到ProviderConfig
     * 3. 调用对应Provider的chat()或chatStream()方法
     */
    private String model;

    /**
     * 消息列表，遵循OpenAI的消息格式
     *
     * 说明：对话的历史消息，每条消息包含role和content
     *
     * 角色说明：
     * - system：系统消息，用于设置AI的行为和角色设定
     * - user：用户消息，用户发送给AI的请求
     * - assistant：助手消息，AI对用户请求的回复
     *
     * 示例：
     * [
     *   {"role": "system", "content": "你是一个友好的客服助手"},
     *   {"role": "user", "content": "你好"},
     *   {"role": "assistant", "content": "你好，有什么可以帮你?"}
     * ]
     */
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * 温度参数，控制输出的随机性
     *
     * 值范围: 0.0 - 2.0
     *
     * 使用建议：
     * - 较低值(0.2-0.5)：输出更确定性和聚焦，适合 factual/technical 内容
     * - 中等值(0.7)：平衡创造性和确定性，适合通用对话
     * - 较高值(0.8-1.0)：输出更有创意和多样性，适合 creative writing
     *
     * 与topP二选一使用，通常建议只设置其中一个
     */
    private Double temperature;

    /**
     * Top-P (核采样参数)，控制候选词的多样性
     *
     * 值范围: 0.0 - 1.0
     *
     * 说明：
     * - 较低值(如0.1)：只考虑高概率的候选词，输出更确定
     * - 较高值(如1.0)：考虑所有候选词，输出更多样
     *
     * 与temperature二选一使用，通常建议只设置其中一个
     */
    private Double topP;

    /**
     * 最大输出Token数
     *
     * 说明：限制AI生成内容的长度，避免过长的回复
     *
     * 值范围: 1 - 模型支持的最大值
     * - 小模型通常支持4096-8192
     * - 大模型（如GPT-4、Claude-3）支持128000
     *
     * 不设置则使用模型的默认最大长度
     */
    private Integer maxTokens;

    /**
     * 是否使用流式响应（SSE - Server-Sent Events）
     *
     * 说明：流式响应允许AI逐步返回生成的内容，适合长文本场景
     *
     * 使用方式：
     * - true：启用流式响应，通过SSE协议逐步返回AI生成的内容
     * - false（默认）：等待完整响应后一次性返回
     *
     * 流式响应的优势：
     * - 首字节延迟更低，用户更快看到内容
     * - 适合长文本生成（如文章写作、代码生成）
     * - 更好的用户体验
     */
    private Boolean stream = false;

    /**
     * 用户标识，用于追踪和限流
     *
     * 用途：
     * - 用户级别的token限流
     * - 请求追踪和日志记录
     * - 多用户场景下的用户识别
     */
    private String user;
}