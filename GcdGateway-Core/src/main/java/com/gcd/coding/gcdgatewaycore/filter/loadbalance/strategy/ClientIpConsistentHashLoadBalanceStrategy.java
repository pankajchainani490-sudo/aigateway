package com.gcd.coding.gcdgatewaycore.filter.loadbalance.strategy;

import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewayconfig.pojo.ServiceInstance;
import com.gcd.coding.gcdgatewayconfig.util.FilterUtil;
import com.gcd.coding.gcdgatewaycore.algorithm.ConsistentHashing;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

import java.util.List;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.LOAD_BALANCE_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.LoadBalanceConstant.CLIENT_IP_CONSISTENT_HASH_LOAD_BALANCE_STRATEGY;

public class ClientIpConsistentHashLoadBalanceStrategy implements LoadBalanceStrategy {

    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        RouteDefinition.LoadBalanceFilterConfig loadBalanceFilterConfig = FilterUtil.findFilterConfigByClass(context.getRoute().getFilterConfigs(), LOAD_BALANCE_FILTER_NAME, RouteDefinition.LoadBalanceFilterConfig.class);
        int virtualNodeNum = 1;
        if (loadBalanceFilterConfig != null && loadBalanceFilterConfig.getVirtualNodeNum() > 0) {
            virtualNodeNum = loadBalanceFilterConfig.getVirtualNodeNum();
        }

        List<String> nodes = instances.stream().map(ServiceInstance::getInstanceId).toList();
        ConsistentHashing consistentHashing = new ConsistentHashing(nodes, virtualNodeNum);
        String selectedNode = consistentHashing.getNode(String.valueOf(context.getRequest().getHost().hashCode()));

        for (ServiceInstance instance : instances) {
            if (instance.getInstanceId().equals(selectedNode)) {
                return instance;
            }
        }

        return instances.get(0);
    }

    @Override
    public String mark() {
        return CLIENT_IP_CONSISTENT_HASH_LOAD_BALANCE_STRATEGY;
    }

}
