package com.gcd.coding.gcdgatewayai.protocol;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OpenAI2AnthropicTransformer implements AIProtocolTransformer {

    @Override
    public String sourceProtocol() {
        return "openai";
    }

    @Override
    public String targetProtocol() {
        return "anthropic";
    }

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
                if ("system".equals(msg.getRole())) {
                    if (systemPrompt.length() > 0) systemPrompt.append("\n");
                    systemPrompt.append(msg.getContent());
                } else {
                    conversationMessages.add(msg);
                }
            }
        }

        if (systemPrompt.length() > 0) {
            ChatMessage systemMsg = new ChatMessage("user", 
                "System instructions: " + systemPrompt.toString());
            transformedMessages.add(systemMsg);
        }
        transformedMessages.addAll(conversationMessages);

        target.setMessages(transformedMessages);
        return target;
    }

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
