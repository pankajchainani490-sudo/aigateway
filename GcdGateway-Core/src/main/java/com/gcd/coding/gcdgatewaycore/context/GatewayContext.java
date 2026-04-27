package com.gcd.coding.gcdgatewaycore.context;

import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewaycore.filter.FilterChain;
import com.gcd.coding.gcdgatewaycore.helper.ContextHelper;
import com.gcd.coding.gcdgatewaycore.request.GatewayRequest;
import com.gcd.coding.gcdgatewaycore.response.GatewayResponse;
import io.netty.channel.ChannelHandlerContext;
import lombok.Data;

@Data
public class GatewayContext {

    private ChannelHandlerContext nettyCtx;

    private Throwable throwable;

    private GatewayRequest request;

    private GatewayResponse response;

    private RouteDefinition route;

    private boolean keepAlive;

    private FilterChain filterChain;

    private int curFilterIndex = 0;
    private boolean isDoPreFilter = true;

    private Object aiConfig;

    private Object resolvedModel;

    private Object aiRequest;

    private Object aiResponse;

    private Object billingRecord;

    private boolean cacheHit = false;

    private String cacheMode;

    private boolean isStreaming = false;

    private boolean shortCircuit = false;

    public GatewayContext(ChannelHandlerContext nettyCtx, GatewayRequest request,
                          RouteDefinition route, boolean keepAlive) {
        this.nettyCtx = nettyCtx;
        this.request = request;
        this.route = route;
        this.keepAlive = keepAlive;
    }

    public void doFilter() {
        if (shortCircuit) {
            ContextHelper.writeBackResponse(this);
            return;
        }
        int size = filterChain.size();
        if (isDoPreFilter) {
            filterChain.doPreFilter(curFilterIndex++, this);
            if (curFilterIndex == size) {
                isDoPreFilter = false;
                curFilterIndex--;
            }
        } else {
            filterChain.doPostFilter(curFilterIndex--, this);
            if (curFilterIndex < 0) {
                ContextHelper.writeBackResponse(this);
            }
        }
    }

    public <T> T getAiConfig(Class<T> clazz) {
        return clazz.cast(aiConfig);
    }

    public <T> T getAiRequest(Class<T> clazz) {
        return clazz.cast(aiRequest);
    }

    public <T> T getAiResponse(Class<T> clazz) {
        return clazz.cast(aiResponse);
    }

    public <T> T getBillingRecord(Class<T> clazz) {
        return clazz.cast(billingRecord);
    }

}
