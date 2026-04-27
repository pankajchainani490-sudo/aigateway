package com.gcd.coding.gcdgatewayconfig.helper;

import com.gcd.coding.gcdgatewaycommon.enums.ResponseCode;
import com.gcd.coding.gcdgatewaycommon.exception.NotFoundException;
import com.gcd.coding.gcdgatewayconfig.manager.DynamicConfigManager;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;

import java.util.*;
import java.util.regex.Pattern;

public class RouteResolver {

    private static final DynamicConfigManager manager = DynamicConfigManager.getInstance();

    /**
     * 根据uri解析出对应的路由
     */
    public static RouteDefinition matchingRouteByUri(String uri) {
        Set<Map.Entry<String, RouteDefinition>> allUriEntry = manager.getAllUriEntry();

        List<RouteDefinition> matchedRoute = new ArrayList<>();

        for (Map.Entry<String, RouteDefinition> entry: allUriEntry) {
            String regex = entry.getKey().replace("**", ".*");
            if (Pattern.matches(regex, uri)) {
                matchedRoute.add(entry.getValue());
            }
        }
        if (matchedRoute.isEmpty()) {
            throw new NotFoundException(ResponseCode.PATH_NO_MATCHED);
        }

        matchedRoute.sort(Comparator.comparingInt(RouteDefinition::getOrder));

        return matchedRoute.stream()
                .min(Comparator.comparingInt(RouteDefinition::getOrder)
                        .thenComparing(route -> route.getUri().length(), Comparator.reverseOrder()))
                .orElseThrow();
    }

}
