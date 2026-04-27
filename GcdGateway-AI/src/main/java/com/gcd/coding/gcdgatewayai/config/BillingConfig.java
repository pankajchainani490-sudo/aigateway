package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;

@Data
public class BillingConfig {

    private boolean enabled = true;

    private String currency = "CNY";

    private boolean logEnabled = true;

}
