package com.gcd.coding.gcdgatewaycore.filter.gray.strategy;

import com.gcd.coding.gcdgatewayconfig.pojo.ServiceInstance;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

import java.util.List;

public interface GrayStrategy {

    boolean shouldRoute2Gray(GatewayContext context, List<ServiceInstance> instances);

    String mark();

}
