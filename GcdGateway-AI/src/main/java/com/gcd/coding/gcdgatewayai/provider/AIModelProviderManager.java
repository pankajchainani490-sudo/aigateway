package com.gcd.coding.gcdgatewayai.provider;

import com.gcd.coding.gcdgatewayai.config.ProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.AsyncHttpClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AIModelProviderManager {

    private static final AIModelProviderManager INSTANCE = new AIModelProviderManager();

    private final Map<String, AIModelProvider> providerMap = new ConcurrentHashMap<>();

    private AsyncHttpClient httpClient;

    private AIModelProviderManager() {
    }

    public static AIModelProviderManager getInstance() {
        return INSTANCE;
    }

    public void setHttpClient(AsyncHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void registerProvider(ProviderConfig config) {
        AIModelProvider provider;
        if ("deepseek".equalsIgnoreCase(config.getType())) {
            provider = new DeepSeekModelProvider(config);
        } else {
            provider = new OpenAIModelProvider(config);
        }
        if (provider instanceof OpenAIModelProvider) {
            ((OpenAIModelProvider) provider).setHttpClient(httpClient);
        }
        providerMap.put(config.getName(), provider);
        log.info("注册AI提供商: {} -> {} ({})", config.getName(), config.getType(), config.getBaseUrl());
    }

    public AIModelProvider getProvider(String name) {
        return providerMap.get(name);
    }

    public Map<String, AIModelProvider> getAllProviders() {
        return providerMap;
    }

    public void clear() {
        providerMap.clear();
    }

}
