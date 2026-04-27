package com.gcd.coding.gcdgatewaycore.filter.route;

import com.gcd.coding.gcdgatewaycommon.enums.ResponseCode;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.Filter;
import com.gcd.coding.gcdgatewaycore.helper.ContextHelper;
import com.gcd.coding.gcdgatewaycore.helper.ResponseHelper;
import com.gcd.coding.gcdgatewaycore.resilience.Resilience;
import org.asynchttpclient.Response;

import java.util.concurrent.CompletableFuture;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.ROUTE_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.ROUTE_FILTER_ORDER;

public class RouteFilter implements Filter {

    @Override
    public void doPreFilter(GatewayContext context) {
        // 如果没有服务定义（如AI服务），跳过路由
        if (context.getRequest().getServiceDefinition() == null) {
            context.doFilter();
            return;
        }

        RouteDefinition.ResilienceConfig resilience = context.getRoute().getResilience();
        if (resilience.isEnabled()) { // 开启弹性配置
            Resilience.getInstance().executeRequest(context);
        } else {
            CompletableFuture<Response> future = RouteUtil.buildRouteSupplier(context).get().toCompletableFuture();
            future.exceptionally(throwable -> {
                context.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.HTTP_RESPONSE_ERROR));
                ContextHelper.writeBackResponse(context);
                return null;
            });
        }
    }

    @Override
    public void doPostFilter(GatewayContext context) {
        context.doFilter();
    }

    @Override
    public String mark() {
        return ROUTE_FILTER_NAME;
    }

    @Override
    public int getOrder() {
        return ROUTE_FILTER_ORDER;
    }

}