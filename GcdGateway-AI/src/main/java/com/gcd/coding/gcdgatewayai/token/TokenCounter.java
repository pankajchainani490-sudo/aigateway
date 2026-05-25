package com.gcd.coding.gcdgatewayai.token;

import com.gcd.coding.gcdgatewayai.model.AIRequest;
import com.gcd.coding.gcdgatewayai.model.AIResponse;
import com.gcd.coding.gcdgatewayai.model.ChatMessage;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.ModelType;

/**
 * Token计数器 - 使用jtokkit库计算Token数量
 *
 * 功能说明：
 * 使用jtokkit库精确计算AI请求和响应中的Token数量。
 * Token是AI模型计费的基本单位，准确计算Token数对于计费和限流至关重要。
 *
 * 主要功能：
 * 1. countInputTokens() - 计算输入Token数（请求）
 * 2. countOutputTokens() - 计算输出Token数（响应）
 * 3. countTokens() - 计算任意文本的Token数
 *
 * Token计算规则：
 * - 输入Token：统计messages中每个role和content的Token数
 * - 输出Token：从响应的usage字段获取，或统计choices中的content
 *
 * 模型编码：
 * - 根据模型名称选择对应的编码（如CL100K_BASE for GPT-4）
 * - 默认使用CL100K_BASE编码
 *
 * 使用场景：
 * - AITokenFilter：前置阶段估算输入Token数
 * - AIBillingFilter：后置阶段计算实际Token消耗进行计费
 * - AITokenPostCorrectionFilter：对比预估算和实际值
 *
 * @see AITokenFilter Token限流过滤器
 * @see AIBillingFilter 计费过滤器
 */
public class TokenCounter {

    /** 默认编码注册表 */
    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    /** 默认编码（CL100K_BASE，用于GPT-4等模型） */
    private static final Encoding DEFAULT_ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    /**
     * 计算输入Token数（AI请求）
     *
     * 统计请求中所有messages的Token数，包括role和content
     *
     * @param request AI请求对象
     * @return 输入Token总数
     */
    public static int countInputTokens(AIRequest request) {
        Encoding encoding = resolveEncoding(request.getModel());
        int tokenCount = 0;
        if (request.getMessages() != null) {
            for (ChatMessage message : request.getMessages()) {
                // 统计role的Token数
                tokenCount += encoding.countTokens(message.getRole() != null ? message.getRole() : "");
                // 统计content的Token数
                tokenCount += encoding.countTokens(message.getContent() != null ? message.getContent() : "");
                // 消息间隔Token（每条消息固定加4）
                tokenCount += 4;
            }
        }
        // 特殊标记Token（请求结束标记）
        tokenCount += 2;
        // max_tokens参数Token（如果设置了）
        if (request.getMaxTokens() != null) {
            tokenCount += 3;
        }
        return tokenCount;
    }

    /**
     * 计算输出Token数（AI响应）
     *
     * 从响应的usage字段获取completionTokens，
     * 如果usage为空则统计choices中的content
     *
     * @param response AI响应对象
     * @return 输出Token总数
     */
    public static int countOutputTokens(AIResponse response) {
        int tokens = 0;
        if (response.getUsage() != null) {
            // 从usage字段直接获取
            tokens = response.getUsage().getCompletionTokens();
        } else if (response.getChoices() != null) {
            // 统计choices中的content
            for (AIResponse.Choice choice : response.getChoices()) {
                if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                    tokens += DEFAULT_ENCODING.countTokens(choice.getMessage().getContent());
                }
            }
        }
        return tokens;
    }

    /**
     * 计算任意文本的Token数
     *
     * @param text 输入文本
     * @return Token数量
     */
    public static int countTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return DEFAULT_ENCODING.countTokens(text);
    }

    /**
     * 根据模型名称解析对应的编码
     *
     * @param modelName 模型名称
     * @return 对应的编码，未知模型使用默认编码
     */
    private static Encoding resolveEncoding(String modelName) {
        if (modelName == null) return DEFAULT_ENCODING;
        try {
            // 尝试根据模型名称获取ModelType
            java.util.Optional<ModelType> modelTypeOpt = ModelType.fromName(modelName);
            if (modelTypeOpt.isPresent()) {
                return REGISTRY.getEncodingForModel(modelTypeOpt.get());
            }
            return DEFAULT_ENCODING;
        } catch (Exception e) {
            // 解析失败，使用默认编码
            return DEFAULT_ENCODING;
        }
    }

}