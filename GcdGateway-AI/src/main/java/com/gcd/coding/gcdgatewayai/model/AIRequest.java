package com.gcd.coding.gcdgatewayai.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class AIRequest {

    private String model;

    private List<ChatMessage> messages = new ArrayList<>();

    private Double temperature;

    private Double topP;

    private Integer maxTokens;

    private Boolean stream = false;

    private String user;

}
