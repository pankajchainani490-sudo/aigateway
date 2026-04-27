package com.gcd.coding.gcdgatewayai.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class AIResponse {

    private String id;

    private String object;

    private long created;

    private String model;

    private List<Choice> choices = new ArrayList<>();

    private Usage usage;

    @Data
    public static class Choice {
        private int index;
        private ChatMessage message;
        private ChatMessage delta;
        private String finishReason;
    }

    @Data
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

}
