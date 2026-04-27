package com.gcd.coding.gcdgatewaycore.filter.loadbalance.strategy;

import com.gcd.coding.gcdgatewayconfig.pojo.ServiceInstance;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

import java.util.List;

public interface LoadBalanceStrategy {

    ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances);

    String mark();

}