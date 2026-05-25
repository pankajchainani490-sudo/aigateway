package com.gcd.coding.gcdgatewayai.model;

import lombok.Data;

/**
 * SSE事件模型 - 用于解析和构建Server-Sent Events格式的数据
 *
 * 功能说明：
 * SSE是一种让服务器向客户端推送数据的轻量级协议。
 * 此模型用于解析和构建SSE格式的事件数据。
 *
 * SSE格式：
 * id:事件ID
 * event:事件类型
 * data:事件数据
 * retry:重试间隔（可选）
 *
 * 空行表示事件结束
 *
 * 使用场景：
 * - 流式响应：AI模型逐步返回生成内容时使用SSE格式
 * - 实时更新：如推送通知、进度更新等
 *
 * @see OpenAIModelProvider#chatStream() 流式响应的SSE处理
 */
@Data
public class SSEEvent {

    /** 事件ID，用于追踪事件 */
    private String id;

    /** 事件类型，如 "message"、"error"、"done" */
    private String event;

    /** 事件数据，JSON格式的内容 */
    private String data;

    /** 重试间隔（毫秒），可选 */
    private Integer retry;

    /**
     * 将SSEEvent转换为SSE格式字符串
     *
     * 格式说明：
     * id:xxx
     * event:xxx
     * data:xxx
     * retry:xxx
     *
     * (空行表示事件结束)
     *
     * @return SSE格式字符串
     */
    public String toSSEString() {
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append("id:").append(id).append("\n");
        }
        if (event != null) {
            sb.append("event:").append(event).append("\n");
        }
        if (data != null) {
            sb.append("data:").append(data).append("\n");
        }
        if (retry != null) {
            sb.append("retry:").append(retry).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 解析原始SSE字符串为SSEEvent对象
     *
     * 解析规则：
     * - 以"id:"开头的行提取ID
     * - 以"event:"开头的行提取事件类型
     * - 以"data:"开头的行提取数据
     * - 以"retry:"开头的行提取重试间隔
     *
     * @param raw 原始SSE字符串
     * @return 解析后的SSEEvent对象
     */
    public static SSEEvent parse(String raw) {
        SSEEvent event = new SSEEvent();
        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line.startsWith("id:")) {
                event.id = line.substring(3).trim();
            } else if (line.startsWith("event:")) {
                event.event = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                event.data = line.substring(5).trim();
            } else if (line.startsWith("retry:")) {
                try {
                    event.retry = Integer.parseInt(line.substring(6).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return event;
    }
}