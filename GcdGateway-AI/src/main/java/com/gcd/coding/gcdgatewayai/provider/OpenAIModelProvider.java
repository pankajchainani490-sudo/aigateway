package com.gcd.coding.gcdgatewayai.provider;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.gcd.coding.gcdgatewayai.config.ProviderConfig;
import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.model.ChatMessage;
import com.gcd.coding.gcdgatewayai.model.SSEEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI模型提供者 - 实现OpenAI API调用
 *
 * 功能说明：
 * 实现OpenAI兼容的AI模型调用，包括chat()和chatStream()两种方式。
 * 支持非流式和流式响应，以及temperature、maxTokens等参数。
 *
 * API调用：
 * - 端点：POST /v1/chat/completions
 * - 认证：Bearer Token
 * - 请求体：OpenAI Chat Completions格式
 *
 * 流式响应：
 * - 使用SSE（Server-Sent Events）协议
 * - 通过AsyncHandler逐块处理响应
 * - 直接写入Netty Channel返回给客户端
 *
 * 使用场景：
 * - 作为基础Provider，供DeepSeek等兼容协议使用
 * - 直接配置type="openai"使用
 *
 * @see AIModelProvider 模型提供者接口
 * @see DeepSeekModelProvider DeepSeek提供者（继承此类）
 */
@Slf4j
public class OpenAIModelProvider implements AIModelProvider {

    /** 提供者配置 */
    private final ProviderConfig config;

    /** 异步HTTP客户端 */
    private AsyncHttpClient httpClient;

    /**
     * 构造函数
     *
     * @param config 提供者配置
     */
    public OpenAIModelProvider(ProviderConfig config) {
        this.config = config;
    }

    /**
     * 设置HTTP客户端
     *
     * @param httpClient 异步HTTP客户端
     */
    public void setHttpClient(AsyncHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 获取Provider名称
     *
     * @return 配置中的name字段
     */
    @Override
    public String providerName() {
        return config.getName();
    }

    /**
     * 获取协议类型
     *
     * @return 配置中的protocol字段
     */
    @Override
    public String protocol() {
        return config.getProtocol();
    }

    /**
     * 是否支持流式响应
     *
     * @return 配置中的supportsStreaming字段
     */
    @Override
    public boolean supportsStreaming() {
        return config.isSupportsStreaming();
    }

    /**
     * 异步非流式聊天请求
     *
     * 调用AI模型并等待完整响应返回
     *
     * @param request AI请求对象
     * @param providerModelId 提供商实际的模型ID
     * @return 包含AI响应的CompletableFuture
     */
    @Override
    public CompletableFuture<AIResponse> chat(AIRequest request, String providerModelId) {
        String url = config.getBaseUrl() + "/v1/chat/completions";

        // 构建请求体
        JSONObject body = buildRequestBody(request, providerModelId, false);

        // 构建HTTP请求
        Request httpRequest = new RequestBuilder()
                .setUrl(url)
                .setMethod("POST")
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + config.getApiKey())
                .setBody(body.toString())
                .setRequestTimeout(config.getRequestTimeout())
                .build();

        // 发送请求并转换为CompletableFuture
        ListenableFuture<Response> future = httpClient.executeRequest(httpRequest);
        return future.toCompletableFuture().thenApply(response -> {
            if (response.getStatusCode() != 200) {
                log.error("OpenAI API error: {} {}", response.getStatusCode(), response.getResponseBody());
                AIResponse errorResp = new AIResponse();
                errorResp.setModel(providerModelId);
                return errorResp;
            }
            return JSONUtil.toBean(response.getResponseBody(), AIResponse.class);
        });
    }

    /**
     * 异步流式聊天请求
     *
     * 调用AI模型并通过SSE逐步返回响应内容
     *
     * @param request AI请求对象
     * @param providerModelId 提供商实际的模型ID
     * @param nettyCtx Netty通道上下文
     */
    @Override
    public void chatStream(AIRequest request, String providerModelId, ChannelHandlerContext nettyCtx) {
        String url = config.getBaseUrl() + "/v1/chat/completions";

        // 构建请求体（stream=true）
        JSONObject body = buildRequestBody(request, providerModelId, true);

        // 构建HTTP请求
        Request httpRequest = new RequestBuilder()
                .setUrl(url)
                .setMethod("POST")
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + config.getApiKey())
                .setBody(body.toString())
                .setRequestTimeout(config.getRequestTimeout())
                .build();

        // 使用AsyncHandler处理SSE流
        httpClient.executeRequest(httpRequest, new AsyncHandler<Object>() {
            private boolean headersSent = false;
            private final StringBuilder sseBuffer = new StringBuilder();

            @Override
            public void onThrowable(Throwable t) {
                log.error("SSE stream error from {}: {}", providerName(), t.getMessage());
                nettyCtx.close();
            }

            @Override
            public State onBodyPartReceived(HttpResponseBodyPart bodyPart) {
                String chunk = new String(bodyPart.getBodyPartBytes(), StandardCharsets.UTF_8);
                sseBuffer.append(chunk);

                // 处理SSE事件（以\n\n分隔）
                while (sseBuffer.indexOf("\n\n") >= 0) {
                    int idx = sseBuffer.indexOf("\n\n") + 2;
                    String eventStr = sseBuffer.substring(0, idx);
                    sseBuffer.delete(0, idx);

                    // 发送HTTP响应头（首次）
                    if (!headersSent) {
                        String httpResponse = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/event-stream\r\n" +
                                "Cache-Control: no-cache\r\n" +
                                "Connection: keep-alive\r\n\r\n";
                        nettyCtx.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(httpResponse.getBytes(StandardCharsets.UTF_8)));
                        headersSent = true;
                    }

                    // 解析SSE事件并发送
                    SSEEvent event = SSEEvent.parse(eventStr);
                    nettyCtx.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(event.toSSEString().getBytes(StandardCharsets.UTF_8)));

                    // 检查是否结束
                    if ("[DONE]".equals(event.getData())) {
                        nettyCtx.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8)));
                        nettyCtx.close();
                        return State.ABORT;
                    }
                }
                return State.CONTINUE;
            }

            @Override
            public State onHeadersReceived(HttpResponseHeaders headers) {
                return State.CONTINUE;
            }

            @Override
            public State onStatusReceived(org.asynchttpclient.HttpResponseStatus responseStatus) {
                if (responseStatus.getStatusCode() != 200) {
                    log.error("SSE connection failed: {}", responseStatus.getStatusCode());
                }
                return State.CONTINUE;
            }

            @Override
            public Object onCompleted() {
                nettyCtx.close();
                return null;
            }
        });
    }

    /**
     * 构建请求体
     *
     * @param request AI请求对象
     * @param providerModelId 提供商实际的模型ID
     * @param stream 是否流式
     * @return JSON格式的请求体
     */
    private JSONObject buildRequestBody(AIRequest request, String providerModelId, boolean stream) {
        JSONObject body = new JSONObject();
        body.set("model", providerModelId);
        body.set("stream", stream);

        // 构建消息列表
        List<JSONObject> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (ChatMessage msg : request.getMessages()) {
                JSONObject m = new JSONObject();
                m.set("role", msg.getRole());
                m.set("content", msg.getContent());
                messages.add(m);
            }
        }
        body.set("messages", messages);

        // 设置可选参数
        if (request.getTemperature() != null) body.set("temperature", request.getTemperature());
        if (request.getTopP() != null) body.set("top_p", request.getTopP());
        if (request.getMaxTokens() != null) body.set("max_tokens", request.getMaxTokens());
        if (request.getUser() != null) body.set("user", request.getUser());

        return body;
    }

}