package com.gcd.coding.gcdgatewayai.filter;

import com.gcd.coding.gcdgatewayai.config.*;
import com.gcd.coding.gcdgatewayai.model.*;
import com.gcd.coding.gcdgatewayai.provider.AIModelProvider;
import com.gcd.coding.gcdgatewayai.provider.AIModelProviderManager;
import com.gcd.coding.gcdgatewaycore.helper.ResponseHelper;
import com.gcd.coding.gcdgatewaycore.response.GatewayResponse;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.Filter;
import lombok.extern.slf4j.Slf4j;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_MODEL_ROUTE_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_MODEL_ROUTE_FILTER_ORDER;

@Slf4j
public class AIModelRouteFilter implements Filter {

    @Override
    public void doPreFilter(GatewayContext context) {
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        if (context.isCacheHit()) {
            context.doFilter();
            return;
        }

        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        if (aiRequest == null || aiRequest.getModel() == null) {
            context.doFilter();
            return;
        }

        ModelConfig modelConfig = findModelConfig(aiConfig, aiRequest.getModel());
        if (modelConfig == null) {
            log.warn("未找到模型配置: {}，跳过AI路由", aiRequest.getModel());
            context.doFilter();
            return;
        }

        context.setResolvedModel(modelConfig);

        ProviderConfig providerConfig = findProviderConfig(aiConfig, modelConfig.getProviderName());
        if (providerConfig == null) {
            log.error("未找到Provider配置: {}", modelConfig.getProviderName());
            GatewayResponse response = ResponseHelper.buildGatewayResponse(
                com.gcd.coding.gcdgatewaycommon.enums.ResponseCode.HTTP_RESPONSE_ERROR);
            context.setResponse(response);
            context.setShortCircuit(true);
            return;
        }

        AIModelProvider provider = AIModelProviderManager.getInstance().getProvider(providerConfig.getName());
        if (provider == null) {
            log.error("未找到Provider实例: {}", providerConfig.getName());
            GatewayResponse response = ResponseHelper.buildGatewayResponse(
                com.gcd.coding.gcdgatewaycommon.enums.ResponseCode.HTTP_RESPONSE_ERROR);
            context.setResponse(response);
            context.setShortCircuit(true);
            return;
        }

        String providerModelId = modelConfig.getProviderModelId() != null
                ? modelConfig.getProviderModelId()
                : aiRequest.getModel();

        log.info("AI模型路由: {} -> {} [{}]", aiRequest.getModel(), providerConfig.getName(), providerModelId);

        boolean useStream = aiRequest.getStream() != null && aiRequest.getStream()
                && modelConfig.isSupportsStreaming()
                && provider.supportsStreaming();

        aiRequest.setModel(providerModelId);

        if (useStream) {
            provider.chatStream(aiRequest, providerModelId, context.getNettyCtx());
        } else {
            provider.chat(aiRequest, providerModelId)
                    .thenAccept(aiResponse -> {
                        context.setAiResponse(aiResponse);
                        context.doFilter();
                    })
                    .exceptionally(throwable -> {
                        log.error("AI调用失败: {}", throwable.getMessage());
                        context.setResponse(ResponseHelper.buildGatewayResponse(
                            com.gcd.coding.gcdgatewaycommon.enums.ResponseCode.HTTP_RESPONSE_ERROR));
                        context.setShortCircuit(true);
                        context.doFilter();
                        return null;
                    });
        }
    }

    @Override
    public void doPostFilter(GatewayContext context) {
        context.doFilter();
    }

    @Override
    public String mark() {
        return AI_MODEL_ROUTE_FILTER_NAME;
    }

    @Override
    public int getOrder() {
        return AI_MODEL_ROUTE_FILTER_ORDER;
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
