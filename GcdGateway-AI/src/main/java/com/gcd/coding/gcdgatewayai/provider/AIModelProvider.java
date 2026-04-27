package com.gcd.coding.gcdgatewayai.provider;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.CompletableFuture;

public interface AIModelProvider {

    String providerName();

    String protocol();

    boolean supportsStreaming();

    CompletableFuture<AIResponse> chat(AIRequest request, String providerModelId);

    void chatStream(AIRequest request, String providerModelId, ChannelHandlerContext nettyCtx);

}
