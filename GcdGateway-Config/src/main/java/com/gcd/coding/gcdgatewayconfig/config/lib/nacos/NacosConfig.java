package com.gcd.coding.gcdgatewayconfig.config.lib.nacos;

import lombok.Data;

import static com.gcd.coding.gcdgatewaycommon.constant.NacosConstant.*;

@Data
public class NacosConfig {

    /**
     * 命名空间，是命名空间id，并非名字
     */
    private String namespace = NACOS_DEFAULT_NAMESPACE;

    /**
     * nacos配置的 Data Id（用于路由配置）
     */
    private String dataId = NACOS_DEFAULT_DATA_ID;

    /**
     * nacos配置的 Group
     */
    private String group = NACOS_DEFAULT_GROUP;

    /**
     * nacos连接超时时长，单位ms
     */
    private int timeout = NACOS_DEFAULT_TIMEOUT;

    /**
     * AI配置在Nacos中的DataId（可选，默认使用gcd-ai-gateway）
     */
    private String aiDataId;

    /**
     * AI配置在Nacos中的Group（可选，默认使用DEFAULT_GROUP）
     */
    private String aiGroup;
}
