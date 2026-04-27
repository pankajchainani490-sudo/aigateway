package com.gcd.coding.gcdgatewayai.protocol;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Anthropic2OpenAITransformer implements AIProtocolTransformer {

    @Override
    public String sourceProtocol() {
        return "anthropic";
    }

    @Override
    public String targetProtocol() {
        return "openai";
    }

    @Override
    public AIRequest transformRequest(AIRequest source) {
        return source;
    }

    @Override
    public AIResponse transformResponse(AIResponse source) {
        return source;
    }

}
