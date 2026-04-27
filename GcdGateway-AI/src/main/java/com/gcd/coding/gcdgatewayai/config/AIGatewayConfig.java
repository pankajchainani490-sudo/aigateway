package com.gcd.coding.gcdgatewayai.config;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class AIGatewayConfig {

    private boolean enabled = true;

    private List<ProviderConfig> providers = new ArrayList<>();

    private List<ModelConfig> models = new ArrayList<>();

    private CacheConfig cache = new CacheConfig();

    private BillingConfig billing = new BillingConfig();

    private TokenConfig token = new TokenConfig();

}
