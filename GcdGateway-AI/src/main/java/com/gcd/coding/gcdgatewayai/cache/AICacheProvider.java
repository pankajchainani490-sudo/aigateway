package com.gcd.coding.gcdgatewayai.cache;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;

public interface AICacheProvider {

    String cacheType();

    AIResponse get(String cacheKey);

    void put(String cacheKey, AIResponse response, long ttlMs);

    boolean contains(String cacheKey);

    String generateKey(AIRequest request);

    long size();

    void clear();

}
