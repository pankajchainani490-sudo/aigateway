package com.gcd.coding.gcdgatewayai.filter;

import cn.hutool.json.JSONUtil;
import com.gcd.coding.gcdgatewayai.cache.*;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfigManager;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfig;
import com.gcd.coding.gcdgatewayai.config.CacheConfig;
import com.gcd.coding.gcdgatewayai.model.*;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.Filter;
import com.gcd.coding.gcdgatewaycore.response.GatewayResponse;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_CACHE_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_CACHE_FILTER_ORDER;

@Slf4j
public class AISemanticCacheFilter implements Filter {

    @Override
    public void doPreFilter(GatewayContext context) {
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        CacheConfig cacheConfig = aiConfig.getCache();
        if (!cacheConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        if (aiRequest == null) {
            context.doFilter();
            return;
        }

        AICacheProvider cacheProvider = AICacheManager.getInstance().getCurrentProvider();
        if (cacheProvider == null) {
            context.doFilter();
            return;
        }

        String cacheKey = cacheProvider.generateKey(aiRequest);

        AIResponse cachedResponse = null;
        String mode = cacheConfig.getMode();

        if ("exact".equalsIgnoreCase(mode)) {
            cachedResponse = cacheProvider.get(cacheKey);
            if (cachedResponse != null) {
                log.info("精确匹配缓存命中!");
            }
        } else if ("embedding".equalsIgnoreCase(mode)) {
            EmbeddingMatchCacheProvider embeddingProvider = AICacheManager.getInstance().getEmbeddingProvider();
            if (embeddingProvider != null) {
                String similarKey = embeddingProvider.findSimilarWithDetails(cacheKey);
                if (similarKey != null) {
                    cachedResponse = embeddingProvider.get(similarKey);
                    if (cachedResponse != null) {
                        log.info("嵌入相似度缓存命中! 阈值: {}", cacheConfig.getSimilarityThreshold());
                    }
                }
            }
        }

        if (cachedResponse != null) {
            context.setCacheHit(true);
            context.setCacheMode(mode);
            context.setAiResponse(cachedResponse);
            context.setShortCircuit(true);

            String responseBody = JSONUtil.toJsonStr(cachedResponse);
            GatewayResponse gatewayResponse = new GatewayResponse();
            gatewayResponse.addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
            gatewayResponse.setHttpResponseStatus(HttpResponseStatus.OK);
            gatewayResponse.setContent(responseBody);
            context.setResponse(gatewayResponse);

            return;
        }

        context.setCacheHit(false);
        context.doFilter();
    }

    @Override
    public void doPostFilter(GatewayContext context) {
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        CacheConfig cacheConfig = aiConfig.getCache();
        if (!cacheConfig.isEnabled() || context.isCacheHit()) {
            context.doFilter();
            return;
        }

        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        AIResponse aiResponse = context.getAiResponse(AIResponse.class);

        if (aiRequest != null && aiResponse != null && aiResponse.getChoices() != null && !aiResponse.getChoices().isEmpty()) {
            AICacheProvider cacheProvider = AICacheManager.getInstance().getCurrentProvider();
            if (cacheProvider != null) {
                String cacheKey = cacheProvider.generateKey(aiRequest);
                cacheProvider.put(cacheKey, aiResponse, cacheConfig.getTtlSeconds() * 1000L);
                log.debug("AI响应已缓存，模式: {}", cacheConfig.getMode());
            }
        }

        context.doFilter();
    }

    @Override
    public String mark() {
        return AI_CACHE_FILTER_NAME;
    }

    @Override
    public int getOrder() {
        return AI_CACHE_FILTER_ORDER;
    }

}
