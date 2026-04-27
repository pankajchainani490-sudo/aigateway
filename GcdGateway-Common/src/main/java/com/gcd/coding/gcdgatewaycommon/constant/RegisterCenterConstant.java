package com.gcd.coding.gcdgatewaycommon.constant;

import com.gcd.coding.gcdgatewaycommon.enums.RegisterCenterEnum;

public interface RegisterCenterConstant {

    // 注册中心默认实现
    RegisterCenterEnum REGISTER_CENTER_DEFAULT_IMPL = RegisterCenterEnum.NACOS;

    // 默认注册中心地址
    String REGISTER_CENTER_DEFAULT_ADDRESS = "127.0.0.1::8848";
}
