package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class ModelConfig {

    private String modelName;

    private String providerName;

    private String providerModelId;

    private double inputPricePer1KTokens = 0.001;

    private double outputPricePer1KTokens = 0.002;

    private double cacheHitDiscount = 1.0;

    private int maxInputTokens = 128000;

    private int maxOutputTokens = 4096;

    private boolean supportsStreaming = true;

    private boolean supportsVision = false;

    private int weight = 100;

}
