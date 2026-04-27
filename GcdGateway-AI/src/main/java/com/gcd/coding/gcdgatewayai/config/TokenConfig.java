package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;

@Data
public class TokenConfig {

    private int defaultRateLimitPerMinute = 100000;

    private int defaultRateLimitPerDay = 10000000;

    private boolean tokenRateLimitEnabled = false;

}
