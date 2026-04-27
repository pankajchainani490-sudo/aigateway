package com.gcd.coding.gcdgatewaycore.helper;

import com.gcd.coding.gcdgatewayconfig.helper.RouteResolver;
import com.gcd.coding.gcdgatewayconfig.manager.DynamicConfigManager;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.request.GatewayRequest;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContextHelper {

    public static GatewayContext buildGatewayContext(FullHttpRequest request, ChannelHandlerContext ctx) {
        RouteDefinition route = RouteResolver.matchingRouteByUri(request.uri());

        GatewayRequest gatewayRequest = RequestHelper.buildGatewayRequest(
                DynamicConfigManager.getInstance().getServiceByName(route.getServiceName()), request, ctx);

        return new GatewayContext(ctx, gatewayRequest, route, HttpUtil.isKeepAlive(request));
    }

    public static void writeBackResponse(GatewayContext context) {
        FullHttpResponse httpResponse = ResponseHelper.buildHttpResponse(context.getResponse());

        if (!context.isKeepAlive()) { // 短连接
            context.getNettyCtx().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
        } else { // 长连接
            httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            context.getNettyCtx().writeAndFlush(httpResponse);
        }
    }

}
