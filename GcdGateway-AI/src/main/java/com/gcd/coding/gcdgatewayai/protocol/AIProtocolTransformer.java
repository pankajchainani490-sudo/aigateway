package com.gcd.coding.gcdgatewayai.protocol;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;

public interface AIProtocolTransformer {

    String sourceProtocol();

    String targetProtocol();

    AIRequest transformRequest(AIRequest source);

    AIResponse transformResponse(AIResponse source);

}
