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

/**
 * AI语义缓存过滤器 - 实现三级语义缓存的查询和写入
 *
 * 功能说明：
 * 在AI请求处理链中，负责缓存的查询和写入：
 * 1. 前置阶段：查询三级缓存（L1精确匹配 → L2 HNSW → L3嵌入相似度）
 * 2. 后置阶段：将AI响应写入三级缓存
 *
 * 三级缓存架构：
 * - L1（精确匹配）：SHA-256哈希精确匹配，O(1)时间复杂度
 * - L2（HNSW向量）：HNSW向量相似度搜索，O(log n)时间复杂度
 * - L3（嵌入相似度）：简单向量相似度，O(n)时间复杂度
 *
 * 缓存命中时：
 * - 设置context.setCacheHit(true)
 * - 设置context.setShortCircuit(true)直接返回
 * - 返回缓存的AI响应
 *
 * 过滤器顺序：AI_CACHE_FILTER_ORDER
 *
 * @see AICacheManager 三级缓存管理器
 * @see ExactMatchCacheProvider L1精确匹配缓存
 * @see HNSWCacheProvider L2 HNSW向量缓存
 * @see EmbeddingMatchCacheProvider L3嵌入相似度缓存
 */
@Slf4j
public class AISemanticCacheFilter implements Filter {

    /**
     * 前置过滤器方法 - 查询缓存
     *
     * @param context 网关上下文
     */
    @Override
    public void doPreFilter(GatewayContext context) {
        // 获取AI配置
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        // 检查缓存是否启用
        CacheConfig cacheConfig = aiConfig.getCache();
        if (!cacheConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        // 获取AI请求
        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        if (aiRequest == null) {
            context.doFilter();
            return;
        }

        // 检查缓存管理器是否初始化
        AICacheManager cacheManager = AICacheManager.getInstance();
        if (cacheManager.getExactProvider() == null) {
            context.doFilter();
            return;
        }

        // 查询三级缓存
        AICacheManager.CacheLookupResult lookupResult = cacheManager.lookupFromThreeLevelCache(aiRequest);

        // 命中缓存
        if (lookupResult.isHit()) {
            context.setCacheHit(true);
            context.setCacheMode(lookupResult.cacheMode());
            context.setAiResponse(lookupResult.response());
            context.setShortCircuit(true);

            // 构建HTTP响应
            String responseBody = JSONUtil.toJsonStr(lookupResult.response());
            GatewayResponse gatewayResponse = new GatewayResponse();
            gatewayResponse.addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
            gatewayResponse.setHttpResponseStatus(HttpResponseStatus.OK);
            gatewayResponse.setContent(responseBody);
            context.setResponse(gatewayResponse);

            log.info("三级缓存命中，模式: {}", lookupResult.cacheMode());
            return;
        }

        // 未命中缓存
        context.setCacheHit(false);
        context.doFilter();
    }

    /**
     * 后置过滤器方法 - 写入缓存
     *
     * @param context 网关上下文
     */
    @Override
    public void doPostFilter(GatewayContext context) {
        // 获取AI配置
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        // 检查缓存是否启用，或是否已命中（不需要重复写入）
        CacheConfig cacheConfig = aiConfig.getCache();
        if (!cacheConfig.isEnabled() || context.isCacheHit()) {
            context.doFilter();
            return;
        }

        // 获取请求和响应
        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        AIResponse aiResponse = context.getAiResponse(AIResponse.class);

        // 写入三级缓存
        if (aiRequest != null && aiResponse != null
                && aiResponse.getChoices() != null
                && !aiResponse.getChoices().isEmpty()) {
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