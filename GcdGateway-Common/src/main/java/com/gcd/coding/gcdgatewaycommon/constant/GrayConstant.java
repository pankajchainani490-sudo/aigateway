package com.gcd.coding.gcdgatewaycommon.constant;

public interface GrayConstant {
    // 服务灰度的最大比例
    double MAX_GRAY_THRESHOLD = 0.95D;

    // 根据流量决定是否灰度的策略名
    String THRESHOLD_GRAY_STRATEGY = "threshold_gray_strategy";

    // 根据用户 ip 决定是否灰度的策略名
    String CLIENT_IP_GRAY_STRATEGY = "client_ip_gray_strategy";
}
