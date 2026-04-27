package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;

@Data
public class CacheConfig {

    private boolean enabled = true;

    private String mode = "exact";

    private int ttlSeconds = 3600;

    private int maxSize = 10000;

    private double similarityThreshold = 0.95;

}
