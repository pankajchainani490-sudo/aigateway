package com.gcd.coding.gcdgatewaycore.filter.loadbalance.strategy;

import com.gcd.coding.gcdgatewayconfig.pojo.ServiceInstance;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.gcd.coding.gcdgatewaycommon.constant.LoadBalanceConstant.QUOTA_WEIGHTED_LOAD_BALANCE_STRATEGY;

@Slf4j
public class QuotaWeightedLoadBalanceStrategy implements LoadBalanceStrategy {

    @Override
    public ServiceInstance selectInstance(GatewayContext context, List<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        int totalQuota = calculateTotalQuota(context);
        if (totalQuota <= 0) {
            return instances.get(ThreadLocalRandom.current().nextInt(instances.size()));
        }

        int randomQuota = ThreadLocalRandom.current().nextInt(totalQuota);
        int cumulativeQuota = 0;

        for (ServiceInstance instance : instances) {
            int instanceQuota = getInstanceQuota(context, instance);
            cumulativeQuota += instanceQuota;
            if (cumulativeQuota > randomQuota) {
                log.debug("Selected instance {} based on quota (total: {}, instance: {})",
                        instance.getInstanceId(), totalQuota, instanceQuota);
                return instance;
            }
        }

        return instances.get(instances.size() - 1);
    }

    @Override
    public String mark() {
        return QUOTA_WEIGHTED_LOAD_BALANCE_STRATEGY;
    }

    private int calculateTotalQuota(GatewayContext context) {
        try {
            Object apiKeyMetadata = context.getApiKeyMetadata();
            if (apiKeyMetadata == null) {
                return 0;
            }
            Method getQuotaRemaining = apiKeyMetadata.getClass().getMethod("getQuotaRemaining");
            Integer quotaRemaining = (Integer) getQuotaRemaining.invoke(apiKeyMetadata);
            return quotaRemaining != null ? quotaRemaining : 0;
        } catch (Exception e) {
            log.debug("Could not get quota from ApiKeyMetadata: {}", e.getMessage());
            return 0;
        }
    }

    private int getInstanceQuota(GatewayContext context, ServiceInstance instance) {
        try {
            Object apiKeyMetadata = context.getApiKeyMetadata();
            if (apiKeyMetadata == null) {
                return instance.getWeight();
            }

            String model = getModelFromContext(context);
            if (model != null) {
                Method getModelQuotas = apiKeyMetadata.getClass().getMethod("getModelQuotas");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Integer> modelQuotas =
                        (java.util.Map<String, Integer>) getModelQuotas.invoke(apiKeyMetadata);
                if (modelQuotas != null && modelQuotas.containsKey(model)) {
                    return Math.max(1, modelQuotas.get(model));
                }
            }

            Method getQuotaRemaining = apiKeyMetadata.getClass().getMethod("getQuotaRemaining");
            Integer quotaRemaining = (Integer) getQuotaRemaining.invoke(apiKeyMetadata);
            return quotaRemaining != null ? Math.max(1, quotaRemaining / Math.max(1, instance.getWeight())) : instance.getWeight();
        } catch (Exception e) {
            log.debug("Could not get specific quota for instance: {}", e.getMessage());
            return instance.getWeight();
        }
    }

    private String getModelFromContext(GatewayContext context) {
        try {
            Object aiRequest = context.getAiRequest();
            if (aiRequest != null) {
                Method getModel = aiRequest.getClass().getMethod("getModel");
                return (String) getModel.invoke(aiRequest);
            }
        } catch (Exception e) {
            log.debug("Could not get model from AI request: {}", e.getMessage());
        }
        return null;
    }
}
