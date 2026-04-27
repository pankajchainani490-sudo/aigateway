package com.gcd.coding.gcdgatewayai;

import cn.hutool.json.JSONUtil;
import com.gcd.coding.gcdgatewayai.billing.AIBillingRecord;
import com.gcd.coding.gcdgatewayai.cache.AICacheManager;
import com.gcd.coding.gcdgatewayai.cache.AICacheProvider;
import com.gcd.coding.gcdgatewayai.config.AIGatewayConfig;
import com.gcd.coding.gcdgatewayai.config.CacheConfig;
import com.gcd.coding.gcdgatewayai.config.ModelConfig;
import com.gcd.coding.gcdgatewayai.config.ProviderConfig;
import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.model.ChatMessage;
import com.gcd.coding.gcdgatewayai.protocol.AIProtocolManager;
import com.gcd.coding.gcdgatewayai.protocol.AIProtocolTransformer;
import com.gcd.coding.gcdgatewayai.provider.AIModelProvider;
import com.gcd.coding.gcdgatewayai.provider.AIModelProviderManager;
import com.gcd.coding.gcdgatewayai.provider.OpenAIModelProvider;
import com.gcd.coding.gcdgatewayai.token.TokenCounter;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class AIGatewayTest {

    private static final String DEEPSEEK_API_KEY = "sk-cd34d611abaf4ab8a7e398bdd3ad3251";

    public static void main(String[] args) throws Exception {
        log.info("========== AI网关功能测试 ==========");

        testTokenCounter();
        testBillingRecord();
        testCache();

        testDeepSeekAPI();

        log.info("========== 所有测试完成 ==========");
    }

    private static void testTokenCounter() {
        log.info("\n--- Token计数测试 ---");
        AIRequest request = new AIRequest();
        request.setModel("deepseek-chat");
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "你是一个AI助手"));
        messages.add(new ChatMessage("user", "你好，请介绍一下自己"));
        request.setMessages(messages);

        int tokens = TokenCounter.countInputTokens(request);
        log.info("输入消息Token计数: {} tokens", tokens);

        String text = "你好，我是DeepSeek AI助手，很高兴为您服务！";
        int textTokens = TokenCounter.countTokens(text);
        log.info("文本 '{}' Token计数: {}", text, textTokens);
    }

    private static void testBillingRecord() {
        log.info("\n--- 计费记录测试（缓存未命中） ---");
        AIBillingRecord record = new AIBillingRecord();
        record.setRequestId("test-req-001");
        record.setModel("deepseek-chat");
        record.setProvider("deepseek");
        record.setClientIp("127.0.0.1");
        record.setCacheHit(false);
        record.setInputTokens(150);
        record.setOutputTokens(80);
        record.setLatencyMs(1200);
        record.calculateCost(0.001, 0.002, 0.5);
        log.info(record.toDisplayString());

        log.info("\n--- 计费记录测试（缓存命中） ---");
        AIBillingRecord record2 = new AIBillingRecord();
        record2.setRequestId("test-req-002");
        record2.setModel("deepseek-chat");
        record2.setProvider("deepseek");
        record2.setClientIp("127.0.0.1");
        record2.setCacheHit(true);
        record2.setCacheMode("exact");
        record2.setInputTokens(150);
        record2.setOutputTokens(0);
        record2.setLatencyMs(5);
        record2.calculateCost(0.001, 0.002, 0.5);
        log.info(record2.toDisplayString());
    }

    private static void testCache() {
        log.info("\n--- 语义缓存测试 ---");
        CacheConfig cacheConfig = new CacheConfig();
        cacheConfig.setEnabled(true);
        cacheConfig.setMode("exact");
        cacheConfig.setTtlSeconds(60);
        cacheConfig.setMaxSize(100);
        cacheConfig.setSimilarityThreshold(0.95);
        AICacheManager.getInstance().init(cacheConfig);

        AIRequest request = new AIRequest();
        request.setModel("deepseek-chat");
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("user", "你好"));
        request.setMessages(messages);

        AICacheProvider cacheProvider = AICacheManager.getInstance().getCurrentProvider();
        String key1 = cacheProvider.generateKey(request);
        log.info("精确匹配缓存key: {} ({} chars)", key1.substring(0, 16) + "...", key1.length());

        log.info("缓存大小: {}", cacheProvider.size());
        log.info("缓存包含key1? {}", cacheProvider.contains(key1));

        AIResponse mockResponse = new AIResponse();
        mockResponse.setModel("deepseek-chat");
        AIResponse.Choice choice = new AIResponse.Choice();
        ChatMessage responseMsg = new ChatMessage("assistant", "你好！有什么可以帮助你的？");
        choice.setMessage(responseMsg);
        choice.setIndex(0);
        mockResponse.getChoices().add(choice);

        cacheProvider.put(key1, mockResponse, 60000);
        log.info("缓存写入后大小: {}", cacheProvider.size());
        log.info("缓存包含key1? {}", cacheProvider.contains(key1));

        AIResponse cached = cacheProvider.get(key1);
        log.info("缓存读取结果: {}", cached != null ? cached.getChoices().get(0).getMessage().getContent() : "null");

        AICacheManager.getInstance().switchMode("embedding");
        log.info("切换到嵌入相似度缓存模式: {}", AICacheManager.getInstance().getCurrentProvider().cacheType());
    }

    private static void testDeepSeekAPI() {
        log.info("\n--- DeepSeek API 测试 ---");

        DefaultAsyncHttpClientConfig.Builder builder = new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(30000)
                .setRequestTimeout(120000);
        AsyncHttpClient httpClient = new DefaultAsyncHttpClient(builder.build());

        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setName("deepseek");
        providerConfig.setType("deepseek");
        providerConfig.setBaseUrl("https://api.deepseek.com");
        providerConfig.setApiKey(DEEPSEEK_API_KEY);
        providerConfig.setProtocol("openai");
        providerConfig.setRequestTimeout(120000);

        AIModelProviderManager.getInstance().setHttpClient(httpClient);
        AIModelProviderManager.getInstance().registerProvider(providerConfig);

        AIModelProvider provider = AIModelProviderManager.getInstance().getProvider("deepseek");

        AIRequest request = new AIRequest();
        request.setModel("deepseek-chat");
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "你是一个有用的AI助手"));
        messages.add(new ChatMessage("user", "用一句话介绍你自己"));
        request.setMessages(messages);
        request.setMaxTokens(100);

        try {
            log.info("发送请求到 DeepSeek API...");
            CompletableFuture<AIResponse> future = provider.chat(request, "deepseek-chat");
            AIResponse response = future.get();

            log.info("DeepSeek响应:");
            log.info("  模型: {}", response.getModel());
            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                log.info("  回复: {}", response.getChoices().get(0).getMessage().getContent());
            }
            if (response.getUsage() != null) {
                log.info("  输入Token: {}", response.getUsage().getPromptTokens());
                log.info("  输出Token: {}", response.getUsage().getCompletionTokens());
                log.info("  总Token: {}", response.getUsage().getTotalTokens());
            }

            AIBillingRecord billing = new AIBillingRecord();
            billing.setRequestId("deepseek-test-1");
            billing.setModel("deepseek-chat");
            billing.setProvider("deepseek");
            billing.setCacheHit(false);
            billing.setInputTokens(response.getUsage() != null ? response.getUsage().getPromptTokens() : 
                    TokenCounter.countInputTokens(request));
            billing.setOutputTokens(response.getUsage() != null ? response.getUsage().getCompletionTokens() : 0);
            billing.calculateCost(0.001, 0.002, 1.0);
            log.info(billing.toDisplayString());

        } catch (Exception e) {
            log.error("DeepSeek API测试失败: {}", e.getMessage(), e);
        } finally {
            try {
                httpClient.close();
            } catch (Exception ignored) {}
        }
    }

}
