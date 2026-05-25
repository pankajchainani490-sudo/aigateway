package com.gcd.coding.gcdgatewayai.filter;

import com.gcd.coding.gcdgatewayai.config.*;
import com.gcd.coding.gcdgatewayai.model.*;
import com.gcd.coding.gcdgatewayai.provider.AIModelProvider;
import com.gcd.coding.gcdgatewayai.provider.AIModelProviderManager;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.Filter;
import com.gcd.coding.gcdgatewaycore.helper.ContextHelper;
import com.gcd.coding.gcdgatewaycore.helper.ResponseHelper;
import com.gcd.coding.gcdgatewaycore.response.GatewayResponse;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.FullHttpResponse;
import lombok.extern.slf4j.Slf4j;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_MODEL_ROUTE_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_MODEL_ROUTE_FILTER_ORDER;

/**
 * AI模型路由过滤器 - 核心过滤器，负责将AI请求路由到对应的AI Provider
 *
 * 功能说明：
 * 这是AI网关的核心过滤器，负责：
 * 1. 从请求中获取AIRequest（包含model、messages等）
 * 2. 根据model名称查找对应的ModelConfig配置
 * 3. 根据ModelConfig中的providerName查找对应的ProviderConfig配置
 * 4. 获取对应的AI Provider实例（DeepSeek/OpenAI等）
 * 5. 根据是否支持流式，选择调用chat()或chatStream()
 * 6. 异步调用Provider并将响应写回客户端
 *
 * 工作流程：
 * 1. 检查AI配置是否启用
 * 2. 检查是否命中缓存（缓存命中则直接返回，跳过AI调用）
 * 3. 根据model查找ModelConfig和ProviderConfig
 * 4. 确定是否使用流式响应
 * 5. 调用对应的Provider方法
 * 6. 将AI响应转换为网关响应并写回客户端
 *
 * 过滤器顺序：AI_MODEL_ROUTE_FILTER_ORDER，在协议转换、Token计数、语义缓存之后执行
 *
 * @see AIModelProviderManager 提供商管理器
 * @see ModelConfig 模型配置
 * @see ProviderConfig 提供商配置
 */
@Slf4j
public class AIModelRouteFilter implements Filter {

    /**
     * 前置过滤器方法 - 执行AI模型的路由和调用
     *
     * @param context 网关上下文
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 第1步：获取AI网关配置
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();

        // 第2步：检查AI功能是否启用
        if (aiConfig == null || !aiConfig.isEnabled()) {
            log.debug("AI配置未启用，跳过");
            context.doFilter();
            return;
        }

        // 第3步：检查是否命中缓存
        if (context.isCacheHit()) {
            log.debug("缓存命中，跳过AI调用");
            context.doFilter();
            return;
        }

        // 第4步：从上下文获取AI请求
        AIRequest aiRequest = context.getAiRequest(AIRequest.class);

        // 第5步：检查请求是否有效
        if (aiRequest == null || aiRequest.getModel() == null) {
            log.debug("aiRequest为空或model为空，跳过AI路由");
            context.doFilter();
            return;
        }

        // 第6步：根据model名称查找模型配置
        ModelConfig modelConfig = findModelConfig(aiConfig, aiRequest.getModel());
        if (modelConfig == null) {
            log.warn("未找到模型配置: {}，跳过AI路由", aiRequest.getModel());
            context.doFilter();
            return;
        }

        // 第7步：存入上下文，供后续过滤器使用
        context.setResolvedModel(modelConfig);

        // 第8步：根据providerName查找提供商配置
        ProviderConfig providerConfig = findProviderConfig(aiConfig, modelConfig.getProviderName());
        if (providerConfig == null) {
            log.error("未找到Provider配置: {}", modelConfig.getProviderName());
            GatewayResponse response = ResponseHelper.buildGatewayResponse(
                com.gcd.coding.gcdgatewaycommon.enums.ResponseCode.HTTP_RESPONSE_ERROR);
            context.setResponse(response);
            context.setShortCircuit(true);
            return;
        }

        // 第9步：获取Provider实例
        AIModelProvider provider = AIModelProviderManager.getInstance().getProvider(providerConfig.getName());
        if (provider == null) {
            log.error("未找到Provider实例: {}", providerConfig.getName());
            GatewayResponse response = ResponseHelper.buildGatewayResponse(
                com.gcd.coding.gcdgatewaycommon.enums.ResponseCode.HTTP_RESPONSE_ERROR);
            context.setResponse(response);
            context.setShortCircuit(true);
            return;
        }

        // 第10步：确定实际调用的模型ID
        String providerModelId = modelConfig.getProviderModelId() != null
                ? modelConfig.getProviderModelId()
                : aiRequest.getModel();

        log.info("AI模型路由: {} -> {} [{}]", aiRequest.getModel(), providerConfig.getName(), providerModelId);

        // 第11步：判断是否使用流式响应
        boolean useStream = aiRequest.getStream() != null && aiRequest.getStream()
                && modelConfig.isSupportsStreaming()
                && provider.supportsStreaming();

        // 第12步：将model替换为providerModelId
        aiRequest.setModel(providerModelId);

        // 第13步：根据是否流式选择不同的调用方式
        if (useStream) {
            // 流式调用
            provider.chatStream(aiRequest, providerModelId, context.getNettyCtx());
        } else {
            // 非流式调用
            provider.chat(aiRequest, providerModelId)
                    .handle((aiResponse, throwable) -> {
                        FullHttpResponse httpResponse;
                        if (throwable != null) {
                            // AI调用失败
                            log.error("AI调用失败: {}", throwable.getMessage());
                            GatewayResponse gatewayResponse = ResponseHelper.buildGatewayResponse(
                                com.gcd.coding.gcdgatewaycommon.enums.ResponseCode.HTTP_RESPONSE_ERROR);
                            httpResponse = ResponseHelper.buildHttpResponse(gatewayResponse);
                        } else {
                            // AI调用成功
                            context.setAiResponse(aiResponse);
                            GatewayResponse gatewayResponse = ResponseHelper.buildGatewayResponse(aiResponse);
                            httpResponse = ResponseHelper.buildHttpResponse(gatewayResponse);
                        }
                        // 写回响应并关闭连接
                        context.getNettyCtx().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
                        return aiResponse;
                    });
            return;
        }
    }

    /**
     * 后置过滤器方法
     *
     * @param context 网关上下文
     */
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

    /**
     * 根据模型名称查找模型配置
     *
     * @param config AI网关配置
     * @param modelName 模型名称
     * @return 模型配置，未找到返回null
     */
    private ModelConfig findModelConfig(AIGatewayConfig config, String modelName) {
        if (config.getModels() == null || modelName == null) return null;
        return config.getModels().stream()
                .filter(m -> m.getModelName().equals(modelName))
                .findFirst().orElse(null);
    }

    /**
     * 根据Provider名称查找Provider配置
     *
     * @param config AI网关配置
     * @param providerName Provider名称
     * @return Provider配置，未找到返回null
     */
    private ProviderConfig findProviderConfig(AIGatewayConfig config, String providerName) {
        if (config.getProviders() == null || providerName == null) return null;
        return config.getProviders().stream()
                .filter(p -> p.getName().equals(providerName))
                .findFirst().orElse(null);
    }
}