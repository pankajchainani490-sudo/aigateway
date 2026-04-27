package com.gcd.coding.gcdgatewaycore.filter.gray.strategy;

import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewayconfig.pojo.ServiceInstance;
import com.gcd.coding.gcdgatewayconfig.util.FilterUtil;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

import java.util.List;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.GRAY_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.GrayConstant.CLIENT_IP_GRAY_STRATEGY;

public class ClientIpGrayStrategy implements GrayStrategy {

    @Override
    public boolean shouldRoute2Gray(GatewayContext context, List<ServiceInstance> instances) {
        if (instances.stream().anyMatch(instance -> instance.isEnabled() && !instance.isGray())) {
            RouteDefinition.GrayFilterConfig grayFilterConfig = FilterUtil.findFilterConfigByClass(context.getRoute().getFilterConfigs(), GRAY_FILTER_NAME, RouteDefinition.GrayFilterConfig.class);
            double grayThreshold = instances.stream().mapToDouble(ServiceInstance::getThreshold).sum();
            grayThreshold = Math.min(grayThreshold, grayFilterConfig.getMaxGrayThreshold());
            return Math.abs(context.getRequest().getHost().hashCode()) % 100 <= grayThreshold * 100;
        }
        return true;
    }

    @Override
    public String mark() {
        return CLIENT_IP_GRAY_STRATEGY;
    }

}
