package com.gcd.coding.gcdgatewaycore.filter.gray.strategy;

import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewayconfig.pojo.ServiceInstance;
import com.gcd.coding.gcdgatewayconfig.util.FilterUtil;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;

import java.util.List;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.GRAY_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.GrayConstant.MAX_GRAY_THRESHOLD;
import static com.gcd.coding.gcdgatewaycommon.constant.GrayConstant.THRESHOLD_GRAY_STRATEGY;

/**
 * 根据流量决定是否灰度策略
 */
public class ThresholdGrayStrategy implements GrayStrategy {

    @Override
    public boolean shouldRoute2Gray(GatewayContext context, List<ServiceInstance> instances) {
        if (instances.stream().anyMatch(instance -> instance.isEnabled() && !instance.isGray())) {
            RouteDefinition.GrayFilterConfig grayFilterConfig = FilterUtil.findFilterConfigByClass(context.getRoute().getFilterConfigs(), GRAY_FILTER_NAME, RouteDefinition.GrayFilterConfig.class);
            double maxGrayThreshold = grayFilterConfig == null ? MAX_GRAY_THRESHOLD : grayFilterConfig.getMaxGrayThreshold();
            double grayThreshold = instances.stream().mapToDouble(ServiceInstance::getThreshold).sum();
            grayThreshold = Math.min(grayThreshold, maxGrayThreshold);
            return Math.abs(Math.random() - 1) <= grayThreshold;
        }
        return true;
    }

    @Override
    public String mark() {
        return THRESHOLD_GRAY_STRATEGY;
    }

}