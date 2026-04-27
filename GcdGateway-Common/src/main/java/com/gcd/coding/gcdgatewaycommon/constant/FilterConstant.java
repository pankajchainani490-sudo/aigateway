package com.gcd.coding.gcdgatewaycommon.constant;

public interface FilterConstant {
    // 跨域过滤器名字
    String CORS_FILTER_NAME = "cors_filter";

    // 跨域过滤器顺序
    int CORS_FILTER_ORDER = Integer.MIN_VALUE;

    // 流控过滤器名字
    String FLOW_FILTER_NAME = "flow_filter";

    // 流控过滤器顺序
    int FLOW_FILTER_ORDER = Integer.MIN_VALUE + 1;

    // 灰度过滤器名字
    String GRAY_FILTER_NAME = "gray_filter";

    // 灰度过滤器顺序
    int GRAY_FILTER_ORDER = Integer.MIN_VALUE + 2;

    // 负载均衡过滤器名字
    String LOAD_BALANCE_FILTER_NAME = "load_balance_filter";

    // 负载均衡过滤器顺序
    int LOAD_BALANCE_FILTER_ORDER = Integer.MIN_VALUE + 3;

    // AI协议对齐过滤器名字
    String AI_PROTOCOL_FILTER_NAME = "ai_protocol_filter";

    // AI协议对齐过滤器顺序
    int AI_PROTOCOL_FILTER_ORDER = Integer.MIN_VALUE + 4;

    // AI Token过滤器名字
    String AI_TOKEN_FILTER_NAME = "ai_token_filter";

    // AI Token过滤器顺序
    int AI_TOKEN_FILTER_ORDER = Integer.MIN_VALUE + 5;

    // AI语义缓存过滤器名字
    String AI_CACHE_FILTER_NAME = "ai_cache_filter";

    // AI语义缓存过滤器顺序
    int AI_CACHE_FILTER_ORDER = Integer.MIN_VALUE + 6;

    // AI模型路由过滤器名字
    String AI_MODEL_ROUTE_FILTER_NAME = "ai_model_route_filter";

    // AI模型路由过滤器顺序
    int AI_MODEL_ROUTE_FILTER_ORDER = Integer.MIN_VALUE + 7;

    // AI计费过滤器名字
    String AI_BILLING_FILTER_NAME = "ai_billing_filter";

    // AI计费过滤器顺序
    int AI_BILLING_FILTER_ORDER = Integer.MIN_VALUE + 8;

    // 路由过滤器名字
    String ROUTE_FILTER_NAME = "route_filter";

    // 路由过滤器顺序
    int ROUTE_FILTER_ORDER = Integer.MAX_VALUE;
}
