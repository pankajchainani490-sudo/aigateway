package com.gcd.coding.gcdgatewayconfig.config;

import com.gcd.coding.gcdgatewaycommon.enums.RegisterCenterEnum;
import com.gcd.coding.gcdgatewayconfig.config.lib.nacos.NacosConfig;
import lombok.Data;

import static com.gcd.coding.gcdgatewaycommon.constant.RegisterCenterConstant.REGISTER_CENTER_DEFAULT_ADDRESS;
import static com.gcd.coding.gcdgatewaycommon.constant.RegisterCenterConstant.REGISTER_CENTER_DEFAULT_IMPL;

/**
 * 注册中心
 */
@Data
public class RegisterCenter {

    private RegisterCenterEnum type = REGISTER_CENTER_DEFAULT_IMPL; // 注册中心实现

    private String address = REGISTER_CENTER_DEFAULT_ADDRESS; // 注册中心地址

    private NacosConfig nacos = new NacosConfig(); // 注册中心nacos配置

}
