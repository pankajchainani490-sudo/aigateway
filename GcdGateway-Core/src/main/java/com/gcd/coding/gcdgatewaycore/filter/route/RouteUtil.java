package com.gcd.coding.gcdgatewaycore.filter.route;

import com.gcd.coding.gcdgatewaycommon.enums.ResponseCode;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.helper.ResponseHelper;
import com.gcd.coding.gcdgatewaycore.http.HttpClient;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class RouteUtil {

    private static final String SSE_CONTENT_TYPE = "text/event-stream";

    public static Supplier<CompletionStage<Response>> buildRouteSupplier(GatewayContext context) {
        return () -> {
            Request request = context.getRequest().build();
            CompletableFuture<Response> future = HttpClient.getInstance().executeRequest(request);
            future.whenComplete(((response, throwable) -> {
                if (throwable != null) {
                    context.setThrowable(throwable);
                    throw new RuntimeException(throwable);
                }
                String contentType = response.getHeader(HttpHeaderNames.CONTENT_TYPE.toString());
                if (contentType != null && contentType.contains(SSE_CONTENT_TYPE)) {
                    context.setStreaming(true);
                    context.setResponse(ResponseHelper.buildGatewayResponseForSSE(response, context));
                } else {
                    context.setResponse(ResponseHelper.buildGatewayResponse(response));
                }
                context.doFilter();
            }));
            return future;
        };
    }

}
