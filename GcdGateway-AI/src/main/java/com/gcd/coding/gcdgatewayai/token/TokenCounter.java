package com.gcd.coding.gcdgatewayai.token;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.model.ChatMessage;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.ModelType;

public class TokenCounter {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    private static final Encoding DEFAULT_ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    public static int countInputTokens(AIRequest request) {
        Encoding encoding = resolveEncoding(request.getModel());
        int tokenCount = 0;
        if (request.getMessages() != null) {
            for (ChatMessage message : request.getMessages()) {
                tokenCount += encoding.countTokens(message.getRole() != null ? message.getRole() : "");
                tokenCount += encoding.countTokens(message.getContent() != null ? message.getContent() : "");
                tokenCount += 4;
            }
        }
        tokenCount += 2;
        if (request.getMaxTokens() != null) {
            tokenCount += 3;
        }
        return tokenCount;
    }

    public static int countOutputTokens(AIResponse response) {
        int tokens = 0;
        if (response.getUsage() != null) {
            tokens = response.getUsage().getCompletionTokens();
        } else if (response.getChoices() != null) {
            for (AIResponse.Choice choice : response.getChoices()) {
                if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                    tokens += DEFAULT_ENCODING.countTokens(choice.getMessage().getContent());
                }
            }
        }
        return tokens;
    }

    public static int countTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return DEFAULT_ENCODING.countTokens(text);
    }

    private static Encoding resolveEncoding(String modelName) {
        if (modelName == null) return DEFAULT_ENCODING;
        try {
            java.util.Optional<ModelType> modelTypeOpt = ModelType.fromName(modelName);
            if (modelTypeOpt.isPresent()) {
                return REGISTRY.getEncodingForModel(modelTypeOpt.get());
            }
            return DEFAULT_ENCODING;
        } catch (Exception e) {
            return DEFAULT_ENCODING;
        }
    }

}
