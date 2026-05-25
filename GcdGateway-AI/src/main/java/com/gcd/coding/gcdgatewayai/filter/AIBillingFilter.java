package com.gcd.coding.gcdgatewayai.filter;

import com.gcd.coding.gcdgatewayai.billing.AIBillingRecord;
import com.gcd.coding.gcdgatewayai.config.*;
import com.gcd.coding.gcdgatewayai.model.*;
import com.gcd.coding.gcdgatewayai.token.TokenCounter;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.Filter;
import lombok.extern.slf4j.Slf4j;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_BILLING_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_BILLING_FILTER_ORDER;

/**
 * AI计费过滤器 - 记录每次AI请求的计费信息
 *
 * 功能说明：
 * 在AI请求完成后，记录详细的计费信息，包括输入/输出Token数、费用计算、延迟等。
 * 支持缓存命中时的特殊计费规则（缓存命中时输出费用享受折扣或为0）。
 *
 * 工作流程：
 * 1. 前置阶段：创建AIBillingRecord，记录请求基本信息
 * 2. 后置阶段：获取实际Token消耗，计算费用，记录延迟
 * 3. 日志输出：根据配置决定是否输出计费明细
 *
 * 计费规则：
 * - 缓存未命中：输入费用 + 输出费用
 * - 缓存命中：输入费用 × 折扣，输出费用 = 0
 *
 * 过滤器顺序：AI_BILLING_FILTER_ORDER，在AI路由之后执行
 *
 * @see AIBillingRecord 计费记录类
 * @see TokenCounter Token计数器
 */
@Slf4j
public class AIBillingFilter implements Filter {

    /**
     * 前置过滤器方法 - 创建计费记录
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

        // 检查计费是否启用
        if (!aiConfig.getBilling().isEnabled()) {
            context.doFilter();
            return;
        }

        // 获取AI请求
        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        if (aiRequest == null) {
            context.doFilter();
            return;
        }

        // 创建计费记录
        AIBillingRecord record = new AIBillingRecord();
        record.setRequestId(context.getRequest().getId());
        record.setClientIp(context.getRequest().getClientIp());
        record.setModel(aiRequest.getModel());

        // 设置提供商名称
        if (context.getResolvedModel() != null) {
            ModelConfig modelConfig = (ModelConfig) context.getResolvedModel();
            record.setProvider(modelConfig.getProviderName());
        }

        // 存入上下文供后置阶段使用
        context.setBillingRecord(record);

        context.doFilter();
    }

    /**
     * 后置过滤器方法 - 计算费用并输出日志
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

        if (!aiConfig.getBilling().isEnabled()) {
            context.doFilter();
            return;
        }

        // 获取计费记录
        AIBillingRecord record = context.getBillingRecord(AIBillingRecord.class);
        if (record == null) {
            context.doFilter();
            return;
        }

        // 获取请求和响应
        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        AIResponse aiResponse = context.getAiResponse(AIResponse.class);

        // 计算Token数
        int inputTokens = 0;
        int outputTokens = 0;

        if (aiRequest != null) {
            inputTokens = TokenCounter.countInputTokens(aiRequest);
        }

        if (aiResponse != null) {
            outputTokens = TokenCounter.countOutputTokens(aiResponse);
        }

        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);

        // 设置缓存信息
        record.setCacheHit(context.isCacheHit());
        record.setCacheMode(context.getCacheMode());

        // 获取定价
        double inputPrice = 0.001;
        double outputPrice = 0.002;
        double cacheDiscount = 1.0;

        if (context.getResolvedModel() != null) {
            ModelConfig modelConfig = (ModelConfig) context.getResolvedModel();
            inputPrice = modelConfig.getInputPricePer1KTokens();
            outputPrice = modelConfig.getOutputPricePer1KTokens();
            cacheDiscount = modelConfig.getCacheHitDiscount();
        }

        // 计算费用
        record.calculateCost(inputPrice, outputPrice, cacheDiscount);

        // 计算延迟
        long latency = System.currentTimeMillis() - context.getRequest().getBeginTime();
        record.setLatencyMs(latency);

        // 输出日志
        if (aiConfig.getBilling().isLogEnabled()) {
            log.info(record.toDisplayString());
        }

        context.doFilter();
    }

    @Override
    public String mark() {
        return AI_BILLING_FILTER_NAME;
    }

    @Override
    public int getOrder() {
        return AI_BILLING_FILTER_ORDER;
    }

}