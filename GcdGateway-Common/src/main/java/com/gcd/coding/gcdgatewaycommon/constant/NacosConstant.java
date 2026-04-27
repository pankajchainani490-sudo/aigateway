package com.gcd.coding.gcdgatewaycommon.constant;

public interface NacosConstant {

    // nacos 默认命名空间，为空，代表 Public
    String NACOS_DEFAULT_NAMESPACE = "";

    // nacos 默认 Data Id
    String NACOS_DEFAULT_DATA_ID = "gcd-gateway";

    // nacos 默认 Group
    String NACOS_DEFAULT_GROUP = "DEFAULT_GROUP";

    // nacos 默认超时时长
    int NACOS_DEFAULT_TIMEOUT = 5000;
}
