package com.gcd.coding.gcdgatewayconfig.manager;

import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;

public interface RouteListener {

    void changeOnRoute(RouteDefinition routeDefinition);

}

