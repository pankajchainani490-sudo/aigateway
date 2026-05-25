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

        AICacheManager cacheManager = AICacheManager.getInstance();
        if (cacheManager.getExactProvider() == null) {
            context.doFilter();
            return;
        }

        AICacheManager.CacheLookupResult lookupResult = cacheManager.lookupFromThreeLevelCache(aiRequest);

        if (lookupResult.isHit()) {
            context.setCacheHit(true);
            context.setCacheMode(lookupResult.cacheMode());
            context.setAiResponse(lookupResult.response());
            context.setShortCircuit(true);

            String responseBody = JSONUtil.toJsonStr(lookupResult.response());
            GatewayResponse gatewayResponse = new GatewayResponse();
            gatewayResponse.addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
            gatewayResponse.setHttpResponseStatus(HttpResponseStatus.OK);
            gatewayResponse.setContent(responseBody);
            context.setResponse(gatewayResponse);

            log.info("三级缓存命中，模式: {}", lookupResult.cacheMode());
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
            AICacheManager cacheManager = AICacheManager.getInstance();
            cacheManager.putToAllLevelCaches(aiRequest, aiResponse);
            log.debug("AI响应已写入三级缓存");
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
