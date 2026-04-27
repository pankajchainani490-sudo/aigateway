package com.gcd.coding.gcdgatewaycore.resilience.fallback;

import com.gcd.coding.gcdgatewaycommon.enums.ResponseCode;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.helper.ContextHelper;
import com.gcd.coding.gcdgatewaycore.helper.ResponseHelper;

import static com.gcd.coding.gcdgatewaycommon.constant.FallbackConstant.DEFAULT_FALLBACK_HANDLER_NAME;

public class DefaultFallbackHandler implements FallbackHandler {

    @Override
    public void handle(Throwable throwable, GatewayContext context) {
        context.setThrowable(throwable);
        context.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.GATEWAY_FALLBACK));
        ContextHelper.writeBackResponse(context);
    }

    @Override
    public String mark() {
        return DEFAULT_FALLBACK_HANDLER_NAME;
    }

}
