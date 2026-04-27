package com.gcd.coding.gcdgatewaycore.filter.cors;

import com.gcd.coding.gcdgatewaycommon.enums.ResponseCode;
import com.gcd.coding.gcdgatewayconfig.config.Config;
import com.gcd.coding.gcdgatewayconfig.manager.DynamicConfigManager;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.Filter;
import com.gcd.coding.gcdgatewaycore.helper.ContextHelper;
import com.gcd.coding.gcdgatewaycore.helper.ResponseHelper;
import com.gcd.coding.gcdgatewaycore.response.GatewayResponse;
import io.netty.handler.codec.http.HttpMethod;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.CORS_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.CORS_FILTER_ORDER;

public class CorsFilter implements Filter {

    @Override
    public void doPreFilter(GatewayContext context) {
        // 获取当前路由的跨域配置（路由级）
        RouteDefinition.CorsFilterConfig corsConfig = getCorsConfig(context);

        // 处理预检请求（OPTIONS）
        if (HttpMethod.OPTIONS.equals(context.getRequest().getMethod())) {
            GatewayResponse response = ResponseHelper.buildGatewayResponse(ResponseCode.SUCCESS);
            setCorsHeaders(response, corsConfig);
            context.setResponse(response);
            ContextHelper.writeBackResponse(context);
            return;
        }

        // 非预检请求继续过滤链
        context.doFilter();
    }

    @Override
    public void doPostFilter(GatewayContext context) {
        // 为响应添加跨域头
        RouteDefinition.CorsFilterConfig corsConfig = getCorsConfig(context);
        setCorsHeaders(context.getResponse(), corsConfig);
        context.doFilter();
    }

    /**
     * 获取有效的跨域配置（路由级优先，否则用全局）
     */
    private RouteDefinition.CorsFilterConfig getCorsConfig(GatewayContext context) {
        // 路由级配置
        RouteDefinition route = context.getRoute();
        return route.getCorsFilterConfig();
    }

    /**
     * 设置跨域响应头
     */
    private void setCorsHeaders(GatewayResponse response, RouteDefinition.CorsFilterConfig config) {
        response.addHeader("Access-Control-Allow-Origin", config.getAllowOrigin());
        response.addHeader("Access-Control-Allow-Methods", config.getAllowMethods());
        response.addHeader("Access-Control-Allow-Headers", config.getAllowHeaders());
        response.addHeader("Access-Control-Allow-Credentials", String.valueOf(config.isAllowCredentials()));
        response.addHeader("Access-Control-Max-Age", String.valueOf(config.getMaxAge()));
    }

    @Override
    public String mark() {
        return CORS_FILTER_NAME;
    }

    @Override
    public int getOrder() {
        return CORS_FILTER_ORDER;
    }
}
