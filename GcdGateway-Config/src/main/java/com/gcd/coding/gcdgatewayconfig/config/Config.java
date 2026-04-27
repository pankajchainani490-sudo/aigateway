package com.gcd.coding.gcdgatewayconfig.config;

import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static com.gcd.coding.gcdgatewaycommon.constant.ConfigConstant.*;

/**
 * 网关静态配置
 */
@Data
public class Config {

    // base
    private String name = DEFAULT_NAME; // 服务名称
    private int port = DEFAULT_PORT; // 端口
    private String env = DEFAULT_ENV; // 环境

    // 配置中心
    private ConfigCenter configCenter = new ConfigCenter();

    // 注册中心
    private RegisterCenter registerCenter = new RegisterCenter();

    // 跨域配置
    private RouteDefinition.CorsFilterConfig globalCorsFilterConfig = new RouteDefinition.CorsFilterConfig();

    // netty
    private NettyConfig netty = new NettyConfig();

    // http client
    private HttpClientConfig httpClient = new HttpClientConfig();

    // 路由配置
    private List<RouteDefinition> routes = new ArrayList<>();

}
