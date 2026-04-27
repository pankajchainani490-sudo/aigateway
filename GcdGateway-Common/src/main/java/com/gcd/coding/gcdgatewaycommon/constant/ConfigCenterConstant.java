package com.gcd.coding.gcdgatewaycommon.constant;

import com.gcd.coding.gcdgatewaycommon.enums.ConfigCenterEnum;

public interface ConfigCenterConstant {
    // 是否开启配置中心，默认关闭
    boolean CONFIG_CENTER_DEFAULT_ENABLED = false;

    // 配置中心默认实现
    ConfigCenterEnum CONFIG_CENTER_DEFAULT_IMPL = ConfigCenterEnum.NACOS;

    // 配置中心默认地址
    String CONFIG_CENTER_DEFAULT_ADDRESS = "127.0.0.1::8848";
}
