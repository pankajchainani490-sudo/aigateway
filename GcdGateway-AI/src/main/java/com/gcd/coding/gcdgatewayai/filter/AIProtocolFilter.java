package com.gcd.coding.gcdgatewayai.filter;

import cn.hutool.json.JSONUtil;
import com.gcd.coding.gcdgatewayai.config.*;
import com.gcd.coding.gcdgatewayai.model.*;
import com.gcd.coding.gcdgatewayai.protocol.AIProtocolManager;
import com.gcd.coding.gcdgatewayai.protocol.AIProtocolTransformer;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.Filter;
import lombok.extern.slf4j.Slf4j;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_PROTOCOL_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_PROTOCOL_FILTER_ORDER;

@Slf4j
public class AIProtocolFilter implements Filter {

    @Override
    public void doPreFilter(GatewayContext context) {
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        try {
            String body = context.getRequest().getFullHttpRequest().content().toString(io.netty.util.CharsetUtil.UTF_8);
            context.getRequest().getFullHttpRequest().content().resetReaderIndex();

            AIRequest aiRequest = JSONUtil.toBean(body, AIRequest.class);
            context.setAiRequest(aiRequest);

            ModelConfig modelConfig = findModelConfig(aiConfig, aiRequest.getModel());
            if (modelConfig != null) {
                ProviderConfig providerConfig = findProviderConfig(aiConfig, modelConfig.getProviderName());
                if (providerConfig != null) {
                    AIProtocolTransformer transformer = AIProtocolManager.getInstance()
                            .getTransformer("openai", providerConfig.getProtocol());
                    if (transformer != null) {
                        log.debug("协议转换: openai -> {}", providerConfig.getProtocol());
                        AIRequest transformed = transformer.transformRequest(aiRequest);
                        context.setAiRequest(transformed);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("AI协议过滤器: 非AI请求，跳过");
        }

        context.doFilter();
    }

    @Override
    public void doPostFilter(GatewayContext context) {
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        try {
            AIRequest aiRequest = context.getAiRequest(AIRequest.class);
            AIResponse aiResponse = context.getAiResponse(AIResponse.class);

            if (aiResponse != null && aiRequest != null) {
                ModelConfig modelConfig = findModelConfig(aiConfig, aiRequest.getModel());
                if (modelConfig != null) {
                    ProviderConfig providerConfig = findProviderConfig(aiConfig, modelConfig.getProviderName());
                    if (providerConfig != null) {
                        AIProtocolTransformer transformer = AIProtocolManager.getInstance()
                                .getTransformer(providerConfig.getProtocol(), "openai");
                        if (transformer != null) {
                            AIResponse transformed = transformer.transformResponse(aiResponse);
                            context.setAiResponse(transformed);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("AI协议过滤器: 响应协议转换跳过");
        }

        context.doFilter();
    }

    @Override
    public String mark() {
        return AI_PROTOCOL_FILTER_NAME;
    }

    @Override
    public int getOrder() {
        return AI_PROTOCOL_FILTER_ORDER;
    }

    private ModelConfig findModelConfig(AIGatewayConfig config, String modelName) {
        if (config.getModels() == null || modelName == null) return null;
        return config.getModels().stream()
                .filter(m -> m.getModelName().equals(modelName))
                .findFirst().orElse(null);
    }

    private ProviderConfig findProviderConfig(AIGatewayConfig config, String providerName) {
        if (config.getProviders() == null || providerName == null) return null;
        return config.getProviders().stream()
                .filter(p -> p.getName().equals(providerName))
                .findFirst().orElse(null);
    }

}
