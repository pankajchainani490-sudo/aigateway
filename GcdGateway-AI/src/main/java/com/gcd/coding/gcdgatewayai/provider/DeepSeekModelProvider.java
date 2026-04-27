package com.gcd.coding.gcdgatewayai.provider;

import com.gcd.coding.gcdgatewayai.config.ProviderConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeepSeekModelProvider extends OpenAIModelProvider {

    public DeepSeekModelProvider(ProviderConfig config) {
        super(config);
    }

    @Override
    public String providerName() {
        return super.providerName();
    }

    @Override
    public String protocol() {
        return "openai";
    }

}
