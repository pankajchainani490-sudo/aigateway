package com.gcd.coding.gcdgatewaycore.filter.gray;

import cn.hutool.json.JSONUtil;
import com.gcd.coding.gcdgatewayconfig.manager.DynamicConfigManager;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewayconfig.pojo.ServiceInstance;
import com.gcd.coding.gcdgatewayconfig.util.FilterUtil;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.Filter;
import com.gcd.coding.gcdgatewaycore.filter.gray.strategy.GrayStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.GRAY_FILTER_NAME;
import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.GRAY_FILTER_ORDER;

@Slf4j
public class GrayFilter implements Filter {

    @Override
    public void doPreFilter(GatewayContext context) {
        // 如果没有服务定义（如下游是AI服务），跳过灰度检查
        if (context.getRequest().getServiceDefinition() == null) {
            context.doFilter();
            return;
        }

        RouteDefinition.FilterConfig filterConfig = FilterUtil.findFilterConfigByName(context.getRoute().getFilterConfigs(), GRAY_FILTER_NAME);
        if (filterConfig == null) {
            filterConfig = FilterUtil.buildDefaultGrayFilterConfig();
        }
        if (!filterConfig.isEnable()) {
            return;
        }

        // 获取服务所有实例
        List<ServiceInstance> instances = DynamicConfigManager.getInstance()
                .getInstancesByServiceName(context.getRequest().getServiceDefinition().getServiceName())
                .values().stream().toList();

        if (instances.stream().anyMatch(instance -> instance.isEnabled() && instance.isGray())) {
            // 存在灰度实例
            GrayStrategy strategy = selectGrayStrategy(JSONUtil.toBean(filterConfig.getConfig(), RouteDefinition.GrayFilterConfig.class));
            context.getRequest().setGray(strategy.shouldRoute2Gray(context, instances));
        } else {
            // 灰度实例都没，不走灰度
            context.getRequest().setGray(false);
        }
        context.doFilter();
    }

    @Override
    public void doPostFilter(GatewayContext context) {
        context.doFilter();
    }

    @Override
    public String mark() {
        return GRAY_FILTER_NAME;
    }

    @Override
    public int getOrder() {
        return GRAY_FILTER_ORDER;
    }

    private GrayStrategy selectGrayStrategy(RouteDefinition.GrayFilterConfig grayFilterConfig) {
        return GrayStrategyManager.getStrategy(grayFilterConfig.getStrategyName());
    }

}

