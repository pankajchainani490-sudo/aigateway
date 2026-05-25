package com.gcd.coding.gcdgatewayai.protocol;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI到Anthropic协议转换器
 *
 * 功能说明：
 * 将OpenAI格式的请求转换为Anthropic格式，以及将Anthropic格式的响应转换回OpenAI格式。
 *
 * 请求转换规则：
 * 1. system消息：从messages中提取并转换为user消息（格式："System instructions: xxx"）
 * 2. 其他消息：保持原样
 * 3. 其他参数：temperature、maxTokens、stream、user直接复制
 *
 * 响应转换规则：
 * 1. 复制id、object、created、model字段
 * 2. choices转换：提取content并包装为OpenAI格式的message
 * 3. usage转换：直接复制promptTokens、completionTokens、totalTokens
 *
 * 使用场景：
 * 当客户端使用OpenAI格式但需要调用Anthropic Claude时，
 * 通过此转换器将请求转换为Anthropic格式，并将响应转回OpenAI格式。
 *
 * @see AIProtocolTransformer 协议转换器接口
 * @see Anthropic2OpenAITransformer 反向转换器
 */
@Slf4j
public class OpenAI2AnthropicTransformer implements AIProtocolTransformer {

    /**
     * 返回源协议类型
     *
     * @return "openai"
     */
    @Override
    public String sourceProtocol() {
        return "openai";
    }

    /**
     * 返回目标协议类型
     *
     * @return "anthropic"
     */
    @Override
    public String targetProtocol() {
        return "anthropic";
    }

    /**
     * 转换AI请求（OpenAI格式 -> Anthropic格式）
     *
     * 转换逻辑：
     * 1. 复制基础字段：model、temperature、maxTokens、stream、user
     * 2. 处理system消息：将system消息内容合并为一个user消息
     * 3. 其他消息保持原样
     *
     * @param source OpenAI格式的AI请求
     * @return Anthropic格式的AI请求
     */
    @Override
    public AIRequest transformRequest(AIRequest source) {
        AIRequest target = new AIRequest();
        target.setModel(source.getModel());
        target.setTemperature(source.getTemperature());
        target.setMaxTokens(source.getMaxTokens());
        target.setStream(source.getStream());
        target.setUser(source.getUser());

        List<ChatMessage> transformedMessages = new ArrayList<>();
        StringBuilder systemPrompt = new StringBuilder();
        List<ChatMessage> conversationMessages = new ArrayList<>();

        if (source.getMessages() != null) {
            for (ChatMessage msg : source.getMessages()) {
                // 提取system消息
                if ("system".equals(msg.getRole())) {
                    if (systemPrompt.length() > 0) systemPrompt.append("\n");
                    systemPrompt.append(msg.getContent());
                } else {
                    conversationMessages.add(msg);
                }
            }
        }

        // 将system消息转换为user消息
        if (systemPrompt.length() > 0) {
            ChatMessage systemMsg = new ChatMessage("user",
                "System instructions: " + systemPrompt.toString());
            transformedMessages.add(systemMsg);
        }
        transformedMessages.addAll(conversationMessages);

        target.setMessages(transformedMessages);
        return target;
    }

    /**
     * 转换AI响应（Anthropic格式 -> OpenAI格式）
     *
     * 转换逻辑：
     * 1. 复制基础字段：id、object、created、model
     * 2. choices转换：从Anthropic的content提取内容，包装为OpenAI格式
     * 3. usage转换：直接复制token统计
     *
     * @param source Anthropic格式的AI响应
     * @return OpenAI格式的AI响应
     */
    @Override
    public AIResponse transformResponse(AIResponse source) {
        if (source == null) return null;

        AIResponse target = new AIResponse();
        target.setId(source.getId());
        target.setObject(source.getObject());
        target.setCreated(source.getCreated());
        target.setModel(source.getModel());

        if (source.getChoices() != null) {
            List<AIResponse.Choice> choices = new ArrayList<>();
            for (AIResponse.Choice srcChoice : source.getChoices()) {
                AIResponse.Choice targetChoice = new AIResponse.Choice();
                targetChoice.setIndex(srcChoice.getIndex());
                targetChoice.setFinishReason(srcChoice.getFinishReason());
                if (srcChoice.getMessage() != null) {
                    ChatMessage targetMsg = new ChatMessage();
                    targetMsg.setRole("assistant");
                    targetMsg.setContent(srcChoice.getMessage().getContent());
                    targetChoice.setMessage(targetMsg);
                }
                choices.add(targetChoice);
            }
            target.setChoices(choices);
        }

        if (source.getUsage() != null) {
            AIResponse.Usage usage = new AIResponse.Usage();
            usage.setPromptTokens(source.getUsage().getPromptTokens());
            usage.setCompletionTokens(source.getUsage().getCompletionTokens());
            usage.setTotalTokens(source.getUsage().getTotalTokens());
            target.setUsage(usage);
        }

        return target;
    }

}