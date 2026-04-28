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
 * 工作流程：
 * 1. 检查AI配置是否启用
 * 2. 检查是否命中缓存（缓存命中则直接返回，跳过AI调用）
 * 3. 从请求中获取AIRequest（包含model、messages等）
 * 4. 根据model名称查找对应的ModelConfig配置
 * 5. 根据ModelConfig中的providerName查找对应的ProviderConfig配置
 * 6. 获取对应的AI Provider实例（DeepSeek/OpenAI等）
 * 7. 根据是否支持流式，选择调用chat()或chatStream()
 * 8. 异步调用Provider并将响应写回客户端
 *
 * 过滤器顺序：AI_MODEL_ROUTE_FILTER_ORDER = Integer.MIN_VALUE + 7
 * 在协议转换、Token计数、语义缓存之后，计费过滤器之前执行
 */
@Slf4j
public class AIModelRouteFilter implements Filter {

    /**
     * 预处理过滤器 - 执行AI模型的路由和调用
     *
     * @param context 网关上下文，包含请求、响应、过滤器链等信息
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 1. 获取AI网关配置（AIGatewayConfigManager是单例，管理AI配置）
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();

        // 2. 检查AI功能是否启用，若未启用则跳过此过滤器，继续后续过滤器
        if (aiConfig == null || !aiConfig.isEnabled()) {
            log.debug("AI配置未启用，跳过");
            context.doFilter();
            return;
        }

        // 3. 检查请求是否命中缓存（语义缓存过滤器会设置此标志）
        // 缓存命中说明已有相同请求的响应，直接使用缓存响应
        if (context.isCacheHit()) {
            log.debug("缓存命中，跳过AI调用");
            context.doFilter();
            return;
        }

        // 4. 从上下文获取AI请求（AIProtocolFilter预处理的请求）
        // AIRequest包含: model(模型名), messages(消息列表), temperature, maxTokens, stream等
        AIRequest aiRequest = context.getAiRequest(AIRequest.class);

        // 5. 检查请求是否有效，必须包含model名称
        if (aiRequest == null || aiRequest.getModel() == null) {
            log.debug("aiRequest为空或model为空，跳过AI路由");
            context.doFilter();
            return;
        }

        // 6. 根据model名称查找对应的模型配置
        // ModelConfig包含：模型名称、provider映射、价格信息、token限制等
        ModelConfig modelConfig = findModelConfig(aiConfig, aiRequest.getModel());
        if (modelConfig == null) {
            log.warn("未找到模型配置: {}，跳过AI路由", aiRequest.getModel());
            context.doFilter();
            return;
        }

        // 7. 将解析后的模型配置存入上下文，供后续过滤器使用（如计费过滤器）
        context.setResolvedModel(modelConfig);

        // 8. 根据模型配置中的providerName查找对应的Provider配置
        // ProviderConfig包含：provider类型(DeepSeek/OpenAI)、baseUrl、apiKey、超时配置等
        ProviderConfig providerConfig = findProviderConfig(aiConfig, modelConfig.getProviderName());
        if (providerConfig == null) {
            log.error("未找到Provider配置: {}", modelConfig.getProviderName());
            GatewayResponse response = ResponseHelper.buildGatewayResponse(
                com.gcd.coding.gcdgatewaycommon.enums.ResponseCode.HTTP_RESPONSE_ERROR);
            context.setResponse(response);
            context.setShortCircuit(true);
            return;
        }

        // 9. 从ProviderManager获取实际的Provider实例
        // ProviderManager管理所有注册的Provider（如DeepSeekModelProvider、OpenAIModelProvider）
        AIModelProvider provider = AIModelProviderManager.getInstance().getProvider(providerConfig.getName());
        if (provider == null) {
            log.error("未找到Provider实例: {}", providerConfig.getName());
            GatewayResponse response = ResponseHelper.buildGatewayResponse(
                com.gcd.coding.gcdgatewaycommon.enums.ResponseCode.HTTP_RESPONSE_ERROR);
            context.setResponse(response);
            context.setShortCircuit(true);
            return;
        }

        // 10. 确定实际调用的模型ID（providerModelId）
        // 如果配置了providerModelId则使用，否则使用请求中的model名称
        String providerModelId = modelConfig.getProviderModelId() != null
                ? modelConfig.getProviderModelId()
                : aiRequest.getModel();

        log.info("AI模型路由: {} -> {} [{}]", aiRequest.getModel(), providerConfig.getName(), providerModelId);

        // 11. 判断是否使用流式响应
        // 流式需要满足：请求要求stream + 模型支持流式 + Provider支持流式
        boolean useStream = aiRequest.getStream() != null && aiRequest.getStream()
                && modelConfig.isSupportsStreaming()
                && provider.supportsStreaming();

        // 12. 将请求中的model替换为providerModelId（统一格式）
        aiRequest.setModel(providerModelId);

        // 13. 根据是否流式选择不同的调用方式
        if (useStream) {
            // 流式调用：直接通过Netty上下文处理SSE流
            // chatStream方法会直接将SSE事件写入Channel，无需等待完成
            provider.chatStream(aiRequest, providerModelId, context.getNettyCtx());
        } else {
            // 非流式调用：异步调用Provider的chat方法
            // 使用CompletableFuture.handle()处理响应和异常
            provider.chat(aiRequest, providerModelId)
                    .handle((aiResponse, throwable) -> {
                        FullHttpResponse httpResponse;
                        if (throwable != null) {
                            // AI调用失败，设置错误响应
                            log.error("AI调用失败: {}", throwable.getMessage());
                            GatewayResponse gatewayResponse = ResponseHelper.buildGatewayResponse(
                                com.gcd.coding.gcdgatewaycommon.enums.ResponseCode.HTTP_RESPONSE_ERROR);
                            httpResponse = ResponseHelper.buildHttpResponse(gatewayResponse);
                        } else {
                            // AI调用成功，将AI响应转换为网关响应格式
                            context.setAiResponse(aiResponse);
                            GatewayResponse gatewayResponse = ResponseHelper.buildGatewayResponse(aiResponse);
                            httpResponse = ResponseHelper.buildHttpResponse(gatewayResponse);
                        }
                        // 直接通过Netty写回响应（在HTTP客户端线程执行）
                        // 添加CLOSE监听器，发送响应后关闭连接
                        context.getNettyCtx().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
                        return aiResponse;
                    });
            // 立即返回，不阻塞Netty事件循环
            // 响应会通过回调异步写回
            return;
        }
    }

    /**
     * 后置过滤器 - AI路由过滤器后置处理
     * 当前为空实现，实际响应处理在doPreFilter的异步回调中完成
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
     * @param config AI网关配置（包含所有providers和models配置）
     * @param modelName 模型名称（如"deepseek-chat"）
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
     * @param providerName Provider名称（如"deepseek"）
     * @return Provider配置，未找到返回null
     */
    private ProviderConfig findProviderConfig(AIGatewayConfig config, String providerName) {
        if (config.getProviders() == null || providerName == null) return null;
        return config.getProviders().stream()
                .filter(p -> p.getName().equals(providerName))
                .findFirst().orElse(null);
    }
}
