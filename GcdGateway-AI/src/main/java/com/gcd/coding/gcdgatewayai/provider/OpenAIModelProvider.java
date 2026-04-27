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

@Slf4j
public class OpenAIModelProvider implements AIModelProvider {

    private final ProviderConfig config;

    private AsyncHttpClient httpClient;

    public OpenAIModelProvider(ProviderConfig config) {
        this.config = config;
    }

    public void setHttpClient(AsyncHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String providerName() {
        return config.getName();
    }

    @Override
    public String protocol() {
        return config.getProtocol();
    }

    @Override
    public boolean supportsStreaming() {
        return config.isSupportsStreaming();
    }

    @Override
    public CompletableFuture<AIResponse> chat(AIRequest request, String providerModelId) {
        String url = config.getBaseUrl() + "/v1/chat/completions";

        JSONObject body = buildRequestBody(request, providerModelId, false);
        Request httpRequest = new RequestBuilder()
                .setUrl(url)
                .setMethod("POST")
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + config.getApiKey())
                .setBody(body.toString())
                .setRequestTimeout(config.getRequestTimeout())
                .build();

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

    @Override
    public void chatStream(AIRequest request, String providerModelId, ChannelHandlerContext nettyCtx) {
        String url = config.getBaseUrl() + "/v1/chat/completions";

        JSONObject body = buildRequestBody(request, providerModelId, true);
        Request httpRequest = new RequestBuilder()
                .setUrl(url)
                .setMethod("POST")
                .setHeader("Content-Type", "application/json")
                .setHeader("Authorization", "Bearer " + config.getApiKey())
                .setBody(body.toString())
                .setRequestTimeout(config.getRequestTimeout())
                .build();

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

                while (sseBuffer.indexOf("\n\n") >= 0) {
                    int idx = sseBuffer.indexOf("\n\n") + 2;
                    String eventStr = sseBuffer.substring(0, idx);
                    sseBuffer.delete(0, idx);

                    if (!headersSent) {
                        String httpResponse = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/event-stream\r\n" +
                                "Cache-Control: no-cache\r\n" +
                                "Connection: keep-alive\r\n\r\n";
                        nettyCtx.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(httpResponse.getBytes(StandardCharsets.UTF_8)));
                        headersSent = true;
                    }

                    SSEEvent event = SSEEvent.parse(eventStr);
                    nettyCtx.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(event.toSSEString().getBytes(StandardCharsets.UTF_8)));

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

    private JSONObject buildRequestBody(AIRequest request, String providerModelId, boolean stream) {
        JSONObject body = new JSONObject();
        body.set("model", providerModelId);
        body.set("stream", stream);

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

        if (request.getTemperature() != null) body.set("temperature", request.getTemperature());
        if (request.getTopP() != null) body.set("top_p", request.getTopP());
        if (request.getMaxTokens() != null) body.set("max_tokens", request.getMaxTokens());
        if (request.getUser() != null) body.set("user", request.getUser());

        return body;
    }

}
