package com.gcd.coding.gcdgatewayai.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * AI响应模型 - 封装AI模型返回的响应数据
 *
 * 功能说明：
 * 遵循OpenAI Chat Completions API响应格式，包含响应ID、模型信息、
 * 生成内容、Token使用量等信息。
 *
 * 主要组成部分：
 * - id：响应唯一标识
 * - model：实际处理的模型名称
 * - choices：生成内容选项列表
 * - usage：Token使用量统计
 *
 * 使用场景：
 * - AIModelRouteFilter：接收Provider返回的AIResponse
 * - AIBillingFilter：从usage字段获取Token数进行计费
 * - AISemanticCacheFilter：从choices获取内容判断是否缓存
 *
 * @see Choice 生成内容选项
 * @see Usage Token使用量统计
 * @see ChatMessage 消息内容结构
 */
@Data
public class AIResponse {

    /**
     * 响应唯一标识
     *
     * 用于追踪和审计请求
     * 示例: "chatcmpl-abc123xyz"
     */
    private String id;

    /**
     * 响应对象类型
     *
     * 示例: "chat.completion"
     */
    private String object;

    /**
     * 响应创建时间戳（Unix时间戳，秒级）
     *
     * 示例: 1712500000
     */
    private long created;

    /**
     * 实际处理的模型名称
     *
     * 说明：可能与请求中的model不同
     * 示例：
     * - 请求model: "deepseek-chat"
     * - 响应model: "deepseek-v4-flash"
     *
     * 用于日志和计费
     */
    private String model;

    /**
     * 生成内容选项列表
     *
     * 说明：通常只有一个选项（index=0）
     * 多选项用于batch或parallel请求场景
     *
     * 字段说明：
     * - message：完整的AI回复消息（非流式响应时使用）
     * - delta：增量内容（流式响应时使用）
     * - finishReason：生成结束原因
     */
    private List<Choice> choices = new ArrayList<>();

    /**
     * Token使用量统计
     *
     * 用于：
     * - 计费结算（根据promptTokens和completionTokens计算费用）
     * - 监控分析（了解请求的token消耗）
     * - 限流控制（基于token数进行流量控制）
     */
    private Usage usage;

    /**
     * 生成内容选项 - 表示AI生成的一个完整回复
     *
     * 在非流式响应中，使用message字段包含完整的AI回复
     * 在流式响应中，使用delta字段包含增量内容
     */
    @Data
    public static class Choice {

        /**
         * 选项索引
         *
         * 通常为0，表示第一个（也是唯一一个）选项
         */
        private int index;

        /**
         * 完整的AI回复消息（非流式响应时使用）
         *
         * 包含role和content
         * 示例: {"role": "assistant", "content": "你好！有什么可以帮助你的？"}
         */
        private ChatMessage message;

        /**
         * 增量消息（流式响应时使用）
         *
         * 每次SSE事件携带一个delta，包含新生成的内容
         * 示例: {"role": "assistant", "content": "你好"}
         */
        private ChatMessage delta;

        /**
         * 生成结束原因
         *
         * 可选值:
         * - "stop": 正常停止（AI认为已完成回复）
         * - "length": 达到maxTokens限制
         * - "content_filter": 内容被过滤
         * - "function_call": 函数调用结束
         */
        private String finishReason;
    }

    /**
     * Token使用量统计
     *
     * 包含输入/输出Token数和总Token数
     */
    @Data
    public static class Usage {

        /**
         * 输入token数量（用户发送的消息）
         *
         * 用于计算输入费用
         */
        private int promptTokens;

        /**
         * 输出token数量（AI生成的消息）
         *
         * 用于计算输出费用
         */
        private int completionTokens;

        /**
         * 总token数量
         *
         * promptTokens + completionTokens
         */
        private int totalTokens;
    }
}