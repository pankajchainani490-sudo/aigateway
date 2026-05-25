package com.gcd.coding.gcdgatewaycore.filter;


import cn.hutool.core.collection.ConcurrentHashSet;
import com.gcd.coding.gcdgatewayconfig.manager.DynamicConfigManager;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.gcd.coding.gcdgatewaycommon.constant.FilterConstant.*;

@Slf4j
public class FilterChainFactory {

    private static final Map<String, Filter> filterMap = new HashMap<>();

    private static final Map<String, FilterChain> filterChainMap = new ConcurrentHashMap<>();

    private static final Set<String> addListener = new ConcurrentHashSet<>();

    static {
        ServiceLoader<Filter> serviceLoader = ServiceLoader.load(Filter.class);
        for (Filter filter : serviceLoader) {
            filterMap.put(filter.mark(), filter);
            log.info("load filter success: {}", filter);
        }
    }


    public static void buildFilterChain(GatewayContext ctx) {
        String serviceName = ctx.getRoute().getServiceName();
        FilterChain chain = filterChainMap.computeIfAbsent(serviceName, name -> {
            FilterChain newChain = new FilterChain();
            addPreFilter(newChain);
            addAIFilter(newChain);
            addFilter(newChain, ctx.getRoute().getFilterConfigs());
            addPostFilter(newChain);
            newChain.sort();
            if (!addListener.contains(serviceName)) {
                DynamicConfigManager.getInstance().addRouteListener(serviceName, newRoute ->
                        filterChainMap.remove(newRoute.getServiceName()));
                addListener.add(serviceName);
            }
            return newChain;
        });
        ctx.setFilterChain(chain);
    }

    private static void addPreFilter(FilterChain chain) {
        addFilterIfPresent(chain, CORS_FILTER_NAME);
        addFilterIfPresent(chain, AUTH_FILTER_NAME);
        addFilterIfPresent(chain, FLOW_FILTER_NAME);
    }

    private static void addAIFilter(FilterChain chain) {
        addFilterIfPresent(chain, AI_CACHE_FILTER_NAME);
        addFilterIfPresent(chain, AI_TOKEN_FILTER_NAME);
        addFilterIfPresent(chain, AI_PROTOCOL_FILTER_NAME);
        addFilterIfPresent(chain, AI_MODEL_ROUTE_FILTER_NAME);
        addFilterIfPresent(chain, AI_BILLING_FILTER_NAME);
        addFilterIfPresent(chain, TOKEN_POST_FILTER_NAME);
    }

    private static void addFilter(FilterChain chain, Set<RouteDefinition.FilterConfig> filterConfigs) {
        if (filterConfigs == null || filterConfigs.isEmpty()) return;
        for (RouteDefinition.FilterConfig filterConfig : filterConfigs) {
            if (!addFilterIfPresent(chain, filterConfig.getName())) {
                log.info("not found filter: {}", filterConfig.getName());
            }
        }
    }

    private static void addPostFilter(FilterChain chain) {
        addFilterIfPresent(chain, ROUTE_FILTER_NAME);
    }

    private static boolean addFilterIfPresent(FilterChain chain, String filterName) {
        Filter filter = filterMap.get(filterName);
        if (null != filter) {
            chain.add(filter);
            return true;
        }
        return false;
    }


}
