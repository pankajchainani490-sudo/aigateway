package com.gcd.coding.gcdgatewaycore.filter.loadbalance.strategy;

import com.gcd.coding.gcdgatewayconfig.pojo.ServiceInstance;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

import java.util.List;

import static com.gcd.coding.gcdgatewaycommon.constant.LoadBalanceConstant.CLIENT_IP_LOAD_BALANCE_STRATEGY;

public class ClientIpLoadBalanceStrategy implements LoadBalanceStrategy{

    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        return instances.get(Math.abs(context.getRequest().getHost().hashCode()) % instances.size());
    }

    @Override
    public String mark() {
        return CLIENT_IP_LOAD_BALANCE_STRATEGY;
    }

}