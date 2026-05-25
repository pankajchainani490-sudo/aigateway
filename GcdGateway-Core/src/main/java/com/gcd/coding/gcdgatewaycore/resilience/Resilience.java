package com.gcd.coding.gcdgatewaycore.resilience;

import com.gcd.coding.gcdgatewaycommon.enums.ResilienceEnum;
import com.gcd.coding.gcdgatewaycommon.enums.ResponseCode;
import com.gcd.coding.gcdgatewayconfig.pojo.RouteDefinition;
import com.gcd.coding.gcdgatewaycore.context.GatewayContext;
import com.gcd.coding.gcdgatewaycore.filter.route.RouteUtil;
import com.gcd.coding.gcdgatewaycore.helper.ContextHelper;
import com.gcd.coding.gcdgatewaycore.helper.ResponseHelper;
import com.gcd.coding.gcdgatewaycore.resilience.fallback.FallbackHandler;
import com.gcd.coding.gcdgatewaycore.resilience.fallback.FallbackHandlerManager;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.Response;

import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
public class Resilience {

    private static final Resilience INSTANCE = new Resilience();

    ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(10);

    private Resilience() {
    }

    public static Resilience getInstance() {
        return INSTANCE;
    }

    public void executeRequest(GatewayContext gatewayContext) {
        RouteDefinition.ResilienceConfig resilienceConfig = gatewayContext.getRoute().getResilience();
        String serviceName = gatewayContext.getRequest().getServiceDefinition().getServiceName();

        Supplier<CompletionStage<Response>> supplier = RouteUtil.buildRouteSupplier(gatewayContext);

        for (ResilienceEnum resilienceEnum : resilienceConfig.getOrder()) {
            switch (resilienceEnum) {
                case RETRY -> {
                    Retry retry = ResilienceFactory.buildRetry(resilienceConfig, serviceName);
                    if (retry != null) {
                        supplier = Retry.decorateCompletionStage(retry, retryScheduler, supplier);
                    }
                }
                case FALLBACK -> {
                    if (resilienceConfig.isFallbackEnabled()) {
                        Supplier<CompletionStage<Response>> finalSupplier = supplier;
                        supplier = () ->
                                finalSupplier.get().exceptionally(throwable -> {
                                    FallbackHandler handler = FallbackHandlerManager.getHandler(resilienceConfig.getFallbackHandlerName());
                                    handler.handle(throwable, gatewayContext);
                                    return null;
                                });
                    }
                }
                case CIRCUITBREAKER -> {
                    CircuitBreakerConfig baseConfig = buildCircuitBreakerConfig(resilienceConfig);
                    CircuitBreaker circuitBreaker = AdaptiveTimeoutCircuitBreaker.getAdaptiveCircuitBreaker(serviceName, baseConfig);
                    if (circuitBreaker != null) {
                        supplier = CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);
                    }
                }
                case BULKHEAD -> {
                    Bulkhead bulkhead = ResilienceFactory.buildBulkHead(resilienceConfig, serviceName);
                    if (bulkhead != null) {
                        supplier = Bulkhead.decorateCompletionStage(bulkhead, supplier);
                    }
                }
                case THREADPOOLBULKHEAD -> {
                    ThreadPoolBulkhead threadPoolBulkhead = ResilienceFactory.buildThreadPoolBulkhead(resilienceConfig, serviceName);
                    if (threadPoolBulkhead != null) {
                        Supplier<CompletionStage<Response>> finalSupplier = supplier;
                        supplier = () -> {
                            CompletionStage<CompletableFuture<Response>> future =
                                    threadPoolBulkhead.executeSupplier(() -> finalSupplier.get().toCompletableFuture());
                            try {
                                return future.toCompletableFuture().get();
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        };
                    }
                }
            }
        }

        supplier.get().exceptionally(throwable -> {
            handleClassifiedError(throwable, gatewayContext, serviceName);
            return null;
        });
    }

    private CircuitBreakerConfig buildCircuitBreakerConfig(RouteDefinition.ResilienceConfig resilienceConfig) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(resilienceConfig.getFailureRateThreshold())
                .slowCallRateThreshold(resilienceConfig.getSlowCallRateThreshold())
                .waitDurationInOpenState(java.time.Duration.ofMillis(resilienceConfig.getWaitDurationInOpenState()))
                .slowCallDurationThreshold(java.time.Duration.ofSeconds(resilienceConfig.getSlowCallDurationThreshold()))
                .permittedNumberOfCallsInHalfOpenState(resilienceConfig.getPermittedNumberOfCallsInHalfOpenState())
                .minimumNumberOfCalls(resilienceConfig.getMinimumNumberOfCalls())
                .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(resilienceConfig.getSlidingWindowSize())
                .build();
    }

    private void handleClassifiedError(Throwable throwable, GatewayContext gatewayContext, String serviceName) {
        int statusCode = extractStatusCode(throwable);
        RouteDefinition.ResilienceConfig resilienceConfig = gatewayContext.getRoute().getResilience();

        if (statusCode == 429) {
            log.warn("Rate limited (429) for service: {}, triggering quick recovery", serviceName);
            handleRateLimitError(gatewayContext, throwable, resilienceConfig);
        } else if (statusCode >= 500 && statusCode < 600) {
            log.warn("Server error ({}) for service: {}, extending circuit breaker time", statusCode, serviceName);
            handleServerError(gatewayContext, throwable, serviceName, resilienceConfig);
        } else if (statusCode == 408 || isTimeoutError(throwable)) {
            log.warn("Timeout error for service: {}, triggering timeout degradation", serviceName);
            handleTimeoutError(gatewayContext, throwable, serviceName, resilienceConfig);
        } else {
            if (!resilienceConfig.isFallbackEnabled()) {
                gatewayContext.setThrowable(throwable);
                gatewayContext.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.SERVICE_UNAVAILABLE));
                ContextHelper.writeBackResponse(gatewayContext);
            }
        }
    }

    private int extractStatusCode(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null) {
            if (message.contains("429")) {
                return 429;
            } else if (message.contains("408")) {
                return 408;
            } else if (message.contains("500") || message.contains("502") || message.contains("503")) {
                try {
                    for (String part : message.split(" ")) {
                        if (part.length() == 3 && part.matches("\\d{3}")) {
                            return Integer.parseInt(part);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (isTimeoutError(throwable)) {
            return 408;
        }

        return -1;
    }

    private boolean isTimeoutError(Throwable throwable) {
        if (throwable instanceof java.net.SocketTimeoutException
                || throwable instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        String message = throwable.getMessage();
        if (message != null && (message.contains("timeout") || message.contains("Timeout"))) {
            return true;
        }
        Throwable cause = throwable.getCause();
        while (cause != null) {
            if (cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void handleRateLimitError(GatewayContext gatewayContext, Throwable throwable,
                                     RouteDefinition.ResilienceConfig resilienceConfig) {
        if (resilienceConfig.isFallbackEnabled()) {
            FallbackHandler handler = FallbackHandlerManager.getHandler(resilienceConfig.getFallbackHandlerName());
            handler.handle(throwable, gatewayContext);
        } else {
            gatewayContext.setThrowable(throwable);
            gatewayContext.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.TOO_MANY_REQUESTS));
            ContextHelper.writeBackResponse(gatewayContext);
        }
    }

    private void handleServerError(GatewayContext gatewayContext, Throwable throwable, String serviceName,
                                   RouteDefinition.ResilienceConfig resilienceConfig) {
        AdaptiveTimeoutCircuitBreaker.recordResponseTime(serviceName, System.currentTimeMillis(), true);

        if (resilienceConfig.isFallbackEnabled()) {
            FallbackHandler handler = FallbackHandlerManager.getHandler(resilienceConfig.getFallbackHandlerName());
            handler.handle(throwable, gatewayContext);
        } else {
            gatewayContext.setThrowable(throwable);
            gatewayContext.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.SERVICE_UNAVAILABLE));
            ContextHelper.writeBackResponse(gatewayContext);
        }
    }

    private void handleTimeoutError(GatewayContext gatewayContext, Throwable throwable, String serviceName,
                                   RouteDefinition.ResilienceConfig resilienceConfig) {
        AdaptiveTimeoutCircuitBreaker.recordResponseTime(serviceName, System.currentTimeMillis(), true);

        if (resilienceConfig.isFallbackEnabled()) {
            FallbackHandler handler = FallbackHandlerManager.getHandler(resilienceConfig.getFallbackHandlerName());
            handler.handle(throwable, gatewayContext);
        } else {
            gatewayContext.setThrowable(throwable);
            gatewayContext.setResponse(ResponseHelper.buildGatewayResponse(ResponseCode.REQUEST_TIMEOUT));
            ContextHelper.writeBackResponse(gatewayContext);
        }
    }

}
