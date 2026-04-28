package com.gcd.coding.gcdgatewayai.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天消息模型 - 表示AI对话中的单条消息
 *
 * 遵循OpenAI Chat Completions API的消息格式
 * 消息是对话的基本单位，由角色(role)和内容(content)组成
 *
 * 使用@Data注解自动生成getter/setter/equals/hashCode/toString
 */
@Data
public class ChatMessage {

    /**
     * 消息角色，决定消息的来源和用途
     *
     * 可选值：
     * - system: 系统消息，用于设置AI的行为和角色设定
     *   示例: role="system", content="你是一个友好的客服助手"
     * - user: 用户消息，用户发送给AI的请求
     *   示例: role="user", content="我想查询订单"
     * - assistant: 助手消息，AI对用户请求的回复
     *   示例: role="assistant", content="好的，请问您的订单号是？"
     */
    private String role;

    /**
     * 消息内容
     * - system消息: 包含对AI的行为指令
     * - user消息: 用户的请求或问题
     * - assistant消息: AI的回复内容
     */
    private String content;

    /**
     * 消息发送者名称（可选）
     * 用于多用户场景或指定特定身份
     * 示例: "Alice", "Bob"
     */
    private String name;

    /**
     * 函数调用列表（可选）
     * 当使用OpenAI Function Calling/Tools功能时，
     * assistant消息会包含AI请求调用的函数信息
     *
     * 结构示例:
     * toolCalls: [
     *   {
     *     id: "call_123",
     *     type: "function",
     *     function: {
     *       name: "get_weather",
     *       arguments: "{\"city\":\"Beijing\"}"
     *     }
     *   }
     * ]
     */
    private List<ToolCall> toolCalls;

    /**
     * 函数调用ID（可选）
     * 用于标识具体的函数调用，在Function Calling流程中使用
     * 当AI需要调用函数时，后续的用户消息会携带此ID
     */
    private String toolCallId;

    /**
     * 默认构造函数
     * 为JSON反序列化提供支持
     */
    public ChatMessage() {
    }

    /**
     * 构造函数 - 便捷创建消息
     *
     * @param role 消息角色
     * @param content 消息内容
     */
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /**
     * 函数调用结构 - 用于Function Calling功能
     *
     * 当AI需要调用外部函数/Tool时，会在assistant消息中包含此结构
     * 网关需要识别此类消息并触发对应的函数调用逻辑
     */
    @Data
    public static class ToolCall {
        /**
         * 函数调用唯一标识
         * 用于追踪和匹配函数调用结果
         * 示例: "call_abc123xyz"
         */
        private String id;

        /**
         * 调用类型，当前固定为"function"
         */
        private String type;

        /**
         * 函数信息，包含函数名和参数
         */
        private Function function;
    }

    /**
     * 函数定义结构
     */
    @Data
    public static class Function {
        /**
         * 要调用的函数名称
         * 示例: "get_weather", "search_database"
         */
        private String name;

        /**
         * 函数参数（JSON字符串格式）
         * 包含函数执行所需的参数
         * 示例: "{\"city\":\"Beijing\",\"unit\":\"celsius\"}"
         */
        private String arguments;
    }
}
