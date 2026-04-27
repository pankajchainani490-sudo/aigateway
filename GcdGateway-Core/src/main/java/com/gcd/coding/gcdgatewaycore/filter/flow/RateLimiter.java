package com.gcd.coding.gcdgatewaycore.filter.flow;

import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

public interface RateLimiter {

    void tryConsume(GatewayContext context);

}
