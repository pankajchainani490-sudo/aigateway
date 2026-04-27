package com.gcd.coding.gcdgatewaycore.resilience.fallback;

import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

public interface FallbackHandler {

    void handle(Throwable throwable, GatewayContext context);

    String mark();

}
