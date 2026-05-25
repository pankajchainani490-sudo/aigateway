package com.gcd.coding.gcdgatewaycore.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class AdaptiveTimeoutCircuitBreaker {

    private static final Map<String, CircuitBreaker> adaptiveCircuitBreakers = new ConcurrentHashMap<>();

    private static final int RESPONSE_TIME_WINDOW_SIZE = 100;
    private static final double MULTIPLIER = 2.0;
    private static final long MIN_TIMEOUT_MS = 1000;
    private static final long MAX_TIMEOUT_MS = 60000;
    private static final long DEFAULT_TIMEOUT_MS = 30000;

    private static final Map<String, ResponseTimeStats> responseTimeStatsMap = new ConcurrentHashMap<>();

    public static CircuitBreaker getAdaptiveCircuitBreaker(String serviceName, CircuitBreakerConfig baseConfig) {
        return adaptiveCircuitBreakers.computeIfAbsent(serviceName, name -> {
            CircuitBreakerConfig adaptiveConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(baseConfig.getFailureRateThreshold())
                    .slowCallRateThreshold(baseConfig.getSlowCallRateThreshold())
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .slowCallDurationThreshold(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .minimumNumberOfCalls(10)
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(100)
                    .build();

            CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(adaptiveConfig).circuitBreaker(name);
            circuitBreaker.getEventPublisher()
                    .onError(error -> recordResponseTime(name, error.getElapsedDuration().toMillis(), true))
                    .onSuccess(success -> recordResponseTime(name, success.getElapsedDuration().toMillis(), false));

            log.info("AdaptiveTimeoutCircuitBreaker created for service: {}", name);
            return circuitBreaker;
        });
    }

    public static void recordResponseTime(String serviceName, long responseTimeMs, boolean isError) {
        ResponseTimeStats stats = responseTimeStatsMap.computeIfAbsent(serviceName, k -> new ResponseTimeStats());
        stats.addResponseTime(responseTimeMs, isError);

        if (stats.getSampleCount() >= 10) {
            long adaptiveTimeout = stats.calculateAdaptiveTimeout(MULTIPLIER, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS);
            stats.setAdaptiveTimeout(adaptiveTimeout);
            log.debug("Service {} adaptive timeout updated to {}ms (avg error response: {}ms, sample count: {})",
                    serviceName, adaptiveTimeout, stats.getAverageErrorResponseTime(), stats.getSampleCount());
        }
    }

    public static long getAdaptiveTimeout(String serviceName) {
        ResponseTimeStats stats = responseTimeStatsMap.get(serviceName);
        if (stats != null) {
            return stats.getAdaptiveTimeout();
        }
        return DEFAULT_TIMEOUT_MS;
    }

    public static void clearStats(String serviceName) {
        responseTimeStatsMap.remove(serviceName);
        adaptiveCircuitBreakers.remove(serviceName);
        log.info("AdaptiveTimeoutCircuitBreaker stats cleared for service: {}", serviceName);
    }

    private static class ResponseTimeStats {
        private final ConcurrentLinkedDeque<Long> errorResponseTimes = new ConcurrentLinkedDeque<>();
        private final ConcurrentLinkedDeque<Long> successResponseTimes = new ConcurrentLinkedDeque<>();
        private final AtomicLong adaptiveTimeout = new AtomicLong(DEFAULT_TIMEOUT_MS);
        private final AtomicReference<Double> averageErrorResponseTime = new AtomicReference<>(0.0);

        public void addResponseTime(long responseTimeMs, boolean isError) {
            if (isError) {
                errorResponseTimes.addLast(responseTimeMs);
                if (errorResponseTimes.size() > RESPONSE_TIME_WINDOW_SIZE) {
                    errorResponseTimes.removeFirst();
                }
            } else {
                successResponseTimes.addLast(responseTimeMs);
                if (successResponseTimes.size() > RESPONSE_TIME_WINDOW_SIZE) {
                    successResponseTimes.removeFirst();
                }
            }
            updateAverageErrorResponseTime();
        }

        private void updateAverageErrorResponseTime() {
            if (errorResponseTimes.isEmpty()) {
                averageErrorResponseTime.set(0.0);
                return;
            }
            double sum = errorResponseTimes.stream().mapToLong(Long::longValue).sum();
            averageErrorResponseTime.set(sum / errorResponseTimes.size());
        }

        public double getAverageErrorResponseTime() {
            Double avg = averageErrorResponseTime.get();
            return avg != null ? avg : 0.0;
        }

        public long getAdaptiveTimeout() {
            return adaptiveTimeout.get();
        }

        public void setAdaptiveTimeout(long timeout) {
            adaptiveTimeout.set(timeout);
        }

        public int getSampleCount() {
            return errorResponseTimes.size() + successResponseTimes.size();
        }

        public long calculateAdaptiveTimeout(double multiplier, long minTimeout, long maxTimeout) {
            double avgErrorTime = getAverageErrorResponseTime();
            if (avgErrorTime <= 0) {
                return DEFAULT_TIMEOUT_MS;
            }
            long calculated = (long) (avgErrorTime * multiplier);
            return Math.max(minTimeout, Math.min(maxTimeout, calculated));
        }
    }
}
