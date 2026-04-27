package com.gcd.coding.gcdgatewayai.filter;

import com.gcd.coding.gcdgatewayai.config.AIGatewayConfigManager;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfig;
import com.gcd.coding.gcdgatewayai.config.TokenConfig;
import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.token.TokenCounter;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.Filter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_TOKEN_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.AI_TOKEN_FILTER_ORDER;

@Slf4j
public class AITokenFilter implements Filter {

    private final Map<String, AtomicLong> minuteTokenCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> dayTokenCounters = new ConcurrentHashMap<>();
    private long lastMinuteReset = System.currentTimeMillis();
    private long lastDayReset = System.currentTimeMillis();

    @Override
    public void doPreFilter(GatewayContext context) {
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        AIRequest aiRequest = context.getAiRequest(AIRequest.class);
        if (aiRequest == null) {
            context.doFilter();
            return;
        }

        int inputTokens = TokenCounter.countInputTokens(aiRequest);
        context.setAiRequest(aiRequest);

        TokenConfig tokenConfig = aiConfig.getToken();
        if (tokenConfig.isTokenRateLimitEnabled()) {
            String model = aiRequest.getModel() != null ? aiRequest.getModel() : "default";

            AtomicLong minuteCounter = minuteTokenCounters.computeIfAbsent(model, k -> new AtomicLong());
            AtomicLong dayCounter = dayTokenCounters.computeIfAbsent(model, k -> new AtomicLong());

            long now = System.currentTimeMillis();
            if (now - lastMinuteReset > 60000) {
                minuteTokenCounters.clear();
                lastMinuteReset = now;
            }
            if (now - lastDayReset > 86400000) {
                dayTokenCounters.clear();
                lastDayReset = now;
            }

            if (minuteCounter.get() + inputTokens > tokenConfig.getDefaultRateLimitPerMinute()) {
                log.warn("模型 {} 每分钟Token限额({})已达", model, tokenConfig.getDefaultRateLimitPerMinute());
            }
            if (dayCounter.get() + inputTokens > tokenConfig.getDefaultRateLimitPerDay()) {
                log.warn("模型 {} 每日Token限额({})已达", model, tokenConfig.getDefaultRateLimitPerDay());
            }

            minuteCounter.addAndGet(inputTokens);
            dayCounter.addAndGet(inputTokens);
        }

        log.debug("AI请求输入Token计数: {}", inputTokens);
        context.doFilter();
    }

    @Override
    public void doPostFilter(GatewayContext context) {
        AIGatewayConfig aiConfig = AIGatewayConfigManager.getInstance().getConfig();
        if (!aiConfig.isEnabled()) {
            context.doFilter();
            return;
        }

        AIResponse aiResponse = context.getAiResponse(AIResponse.class);
        if (aiResponse != null) {
            int outputTokens = TokenCounter.countOutputTokens(aiResponse);
            log.debug("AI响应输出Token计数: {}", outputTokens);
        }

        context.doFilter();
    }

    @Override
    public String mark() {
        return AI_TOKEN_FILTER_NAME;
    }

    @Override
    public int getOrder() {
        return AI_TOKEN_FILTER_ORDER;
    }

}
