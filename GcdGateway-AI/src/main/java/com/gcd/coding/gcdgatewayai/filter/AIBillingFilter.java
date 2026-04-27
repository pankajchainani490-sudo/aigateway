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

@Slf4j
public class AIBillingFilter implements Filter {

    @Override
    public void doPreFilter(GatewayContext context) {
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        if (!aiConfig.getBilling().isEnabled()) {
            context.doFilter();
            return;
        }

        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        if (aiRequest == null) {
            context.doFilter();
            return;
        }

        AIBillingRecord record = new AIBillingRecord();
        record.setRequestId(context.getRequest().getId());
        record.setClientIp(context.getRequest().getClientIp());
        record.setModel(aiRequest.getModel());

        if (context.getResolvedModel() != null) {
            ModelConfig modelConfig = (ModelConfig) context.getResolvedModel();
            record.setProvider(modelConfig.getProviderName());
        }

        context.setBillingRecord(record);

        context.doFilter();
    }

    @Override
    public void doPostFilter(GatewayContext context) {
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        if (!aiConfig.getBilling().isEnabled()) {
            context.doFilter();
            return;
        }

        AIBillingRecord record = context.getBillingRecord(AIBillingRecord.class);
        if (record == null) {
            context.doFilter();
            return;
        }

        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        AIResponse aiResponse = context.getAiResponse(AIResponse.class);

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

        record.setCacheHit(context.isCacheHit());
        record.setCacheMode(context.getCacheMode());

        double inputPrice = 0.001;
        double outputPrice = 0.002;
        double cacheDiscount = 1.0;

        if (context.getResolvedModel() != null) {
            ModelConfig modelConfig = (ModelConfig) context.getResolvedModel();
            inputPrice = modelConfig.getInputPricePer1KTokens();
            outputPrice = modelConfig.getOutputPricePer1KTokens();
            cacheDiscount = modelConfig.getCacheHitDiscount();
        }

        record.calculateCost(inputPrice, outputPrice, cacheDiscount);

        long latency = System.currentTimeMillis() - context.getRequest().getBeginTime();
        record.setLatencyMs(latency);

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
