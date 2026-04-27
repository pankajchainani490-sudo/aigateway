package com.gcd.coding.gcdgatewaycore.filter.loadbalance.strategy;

import com.gcd.coding.gcdgatewayconfig.pojo.ServiceInstance;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

import java.util.List;

import static com.gcd.coding.gcdgatewaycommon.constant.LoadBalanceConstant.GRAY_LOAD_BALANCE_STRATEGY;

public class GrayLoadBalanceStrategy implements LoadBalanceStrategy {

    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        int totalThreshold = (int) (instances.stream().mapToDouble(ServiceInstance::getThreshold).sum() * 100);
        if (totalThreshold <= 0) return null;
        int randomThreshold = Math.abs(context.getRequest().getHost().hashCode()) % totalThreshold;
        for (ServiceInstance instance : instances) {
            randomThreshold -= instance.getThreshold();
            if (randomThreshold < 0) return instance;
        }
        return null;
    }

    @Override
    public String mark() {
        return GRAY_LOAD_BALANCE_STRATEGY;
    }

}