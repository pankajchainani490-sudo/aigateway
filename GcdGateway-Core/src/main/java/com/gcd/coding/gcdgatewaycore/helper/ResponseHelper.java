package com.gcd.coding.gcdgatewaycore.helper;

import cn.hutool.json.JSONUtil;
import com.gcd.coding.gcdgatewaycommon.enums.ResponseCode;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.response.GatewayResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.Response;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
public class ResponseHelper {

    public static FullHttpResponse buildHttpResponse(GatewayResponse gatewayResponse) {
        ByteBuf content;
        if (Objects.nonNull(gatewayResponse.getResponse())) {
            content = Unpooled.wrappedBuffer(gatewayResponse.getResponse().getResponseBodyAsByteBuffer());
        } else if (gatewayResponse.getContent() != null) {
            content = Unpooled.wrappedBuffer(gatewayResponse.getContent().getBytes(StandardCharsets.UTF_8));
        } else {
            content = Unpooled.wrappedBuffer("".getBytes(StandardCharsets.UTF_8));
        }

        DefaultFullHttpResponse httpResponse;
        if (Objects.nonNull(gatewayResponse.getResponse())) {
            httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    io.netty.handler.codec.http.HttpResponseStatus.valueOf(gatewayResponse.getResponse().getStatusCode()), content);
            httpResponse.headers().add(gatewayResponse.getResponse().getHeaders());
        } else {
            httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    gatewayResponse.getHttpResponseStatus(), content);
            httpResponse.headers().add(gatewayResponse.getResponseHeaders());
            httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
        }

        return httpResponse;
    }

    public static FullHttpResponse buildHttpResponse(ResponseCode responseCode) {
        DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                responseCode.getStatus(),
                Unpooled.wrappedBuffer(responseCode.getMessage().getBytes(StandardCharsets.UTF_8)));
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
        httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());

        return httpResponse;
    }

    public static GatewayResponse buildGatewayResponse(Response response) {
        GatewayResponse gatewayResponse = new GatewayResponse();
        gatewayResponse.setResponseHeaders(response.getHeaders());
        gatewayResponse.setHttpResponseStatus(io.netty.handler.codec.http.HttpResponseStatus.valueOf(response.getStatusCode()));
        gatewayResponse.setContent(response.getResponseBody());
        gatewayResponse.setResponse(response);

        return gatewayResponse;
    }

    public static GatewayResponse buildGatewayResponseForSSE(Response response, GatewayContext context) {
        GatewayResponse gatewayResponse = new GatewayResponse();
        gatewayResponse.setResponseHeaders(response.getHeaders());
        gatewayResponse.setHttpResponseStatus(io.netty.handler.codec.http.HttpResponseStatus.valueOf(response.getStatusCode()));
        gatewayResponse.setContent(response.getResponseBody());
        gatewayResponse.setResponse(response);
        context.setStreaming(true);
        return gatewayResponse;
    }

    public static GatewayResponse buildGatewayResponse(ResponseCode code) {
        GatewayResponse gatewayResponse = new GatewayResponse();
        gatewayResponse.addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
        gatewayResponse.setHttpResponseStatus(code.getStatus());
        gatewayResponse.setContent(JSONUtil.toJsonStr(code.getMessage()));

        return gatewayResponse;
    }

    public static GatewayResponse buildGatewayResponse(Object data) {
        GatewayResponse gatewayResponse = new GatewayResponse();
        gatewayResponse.addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
        gatewayResponse.setHttpResponseStatus(ResponseCode.SUCCESS.getStatus());
        gatewayResponse.setContent(JSONUtil.toJsonStr(data));

        return gatewayResponse;
    }

}
