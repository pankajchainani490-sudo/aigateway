package com.gcd.coding.gcdgatewayai.protocol;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AIProtocolManager {

    private static final AIProtocolManager INSTANCE = new AIProtocolManager();

    private final Map<String, AIProtocolTransformer> transformerMap = new ConcurrentHashMap<>();

    private AIProtocolManager() {
        ServiceLoader<AIProtocolTransformer> loader = ServiceLoader.load(AIProtocolTransformer.class);
        for (AIProtocolTransformer transformer : loader) {
            String key = transformer.sourceProtocol() + "->" + transformer.targetProtocol();
            transformerMap.put(key, transformer);
            log.info("加载协议转换器: {}", key);
        }
        if (transformerMap.isEmpty()) {
            OpenAI2AnthropicTransformer o2a = new OpenAI2AnthropicTransformer();
            transformerMap.put(o2a.sourceProtocol() + "->" + o2a.targetProtocol(), o2a);
            Anthropic2OpenAITransformer a2o = new Anthropic2OpenAITransformer();
            transformerMap.put(a2o.sourceProtocol() + "->" + a2o.targetProtocol(), a2o);
        }
    }

    public static AIProtocolManager getInstance() {
        return INSTANCE;
    }

    public AIProtocolTransformer getTransformer(String sourceProtocol, String targetProtocol) {
        if (sourceProtocol.equals(targetProtocol)) return null;
        return transformerMap.get(sourceProtocol + "->" + targetProtocol);
    }

}
