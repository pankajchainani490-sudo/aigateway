package com.gcd.coding.gcdgatewayai.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * AI请求模型 - 封装发送给AI模型的请求参数
 *
 * 遵循OpenAI Chat Completions API格式，支持以下AI Provider：
 * - DeepSeek (通过DeepSeekModelProvider)
 * - OpenAI (通过OpenAIModelProvider)
 * - Anthropic (通过协议转换器支持)
 *
 * 使用@Data注解自动生成getter/setter/equals/hashCode/toString
 */
@Data
public class AIRequest {

    /**
     * 模型名称
     * 示例: "deepseek-chat", "gpt-4", "claude-3-opus"
     * 网关会根据此名称查找对应的ModelConfig进行路由
     */
    private String model;

    /**
     * 消息列表，遵循OpenAI的消息格式
     * 每个消息包含role(角色)和content(内容)
     * role可选值: system(系统指令), user(用户), assistant(助手)
     *
     * 示例:
     * [
     *   {"role": "system", "content": "你是一个有用的助手"},
     *   {"role": "user", "content": "你好"},
     *   {"role": "assistant", "content": "你好，有什么可以帮你?"}
     * ]
     */
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * 温度参数，控制输出的随机性
     * 值范围: 0.0 - 2.0
     * - 较低的值(如0.2)使输出更确定性和聚焦
     * - 较高的值(如0.8)使输出更有创意和多样性
     * - 默认值通常为0.7
     * 可选参数
     */
    private Double temperature;

    /**
     * Top-P (核采样参数)，控制候选词的多样性
     * 值范围: 0.0 - 1.0
     * - 较低的值(如0.1)只考虑高概率的候选词
     * - 较高的值(如1.0)考虑所有候选词
     * 与temperature二选一使用，通常建议只设置其中一个
     * 可选参数
     */
    private Double topP;

    /**
     * 最大输出Token数
     * 限制AI生成内容的长度，避免过长的回复
     * 值范围: 1 - 模型支持的最大值(如4096, 8192等)
     * 可选参数，不设置则使用模型默认值
     */
    private Integer maxTokens;

    /**
     * 是否使用流式响应(SSE - Server-Sent Events)
     * - true: 启用流式响应，逐块返回AI生成的内容
     * - false: 等待完整响应后一次性返回
     * 流式响应通过SSE协议实现，适合长文本生成场景
     * 可选参数，默认为false
     */
    private Boolean stream = false;

    /**
     * 用户标识，用于追踪和限流
     * 可用于：
     * - 用户级别的token限流
     * - 请求追踪和日志记录
     * - 多用户场景下的用户识别
     * 可选参数
     */
    private String user;
}
