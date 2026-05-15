package com.company.usermanagement.service;

import com.company.usermanagement.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
public class RateLimitService {

    private static final String AUTH_PREFIX = "auth:";
    private static final String API_PREFIX = "api:";

    private final AppProperties appProperties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final CircuitBreaker redisCircuitBreaker;
    private final TimeLimiter redisTimeLimiter;
    
    // Tier-1 Hardened Thread Pool: Bounded queue to prevent OOM during Redis "Gray Failures"
    private final ExecutorService redisExecutor = new ThreadPoolExecutor(
            4, 10, // Core/Max threads
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), // Bounded queue - CRITICAL
            new ThreadPoolExecutor.AbortPolicy() // Reject if full to prevent OOM
    );

    private final Cache<String, Bucket> localBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    private final DefaultRedisScript<Long> incrementScript = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    public RateLimitService(AppProperties appProperties, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.appProperties = appProperties;
        this.redisTemplateProvider = redisTemplateProvider;

        this.redisCircuitBreaker = CircuitBreaker.of("redisRateLimiter", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofMillis(100))
                .slidingWindowSize(20)
                .build());

        this.redisTimeLimiter = TimeLimiter.of("redisRateLimiter", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(100))
                .build());
    }

    public RateLimitDecision check(String key, boolean authEndpoint) {
        String normalizedKey = normalizeKey(key);
        String cacheKey = (authEndpoint ? AUTH_PREFIX : API_PREFIX) + normalizedKey;

        if (shouldUseRedisBackend()) {
            try {
                Supplier<CompletableFuture<RateLimitDecision>> futureSupplier = 
                        () -> CompletableFuture.supplyAsync(() -> checkRedis(cacheKey, authEndpoint), redisExecutor);
                
                Callable<RateLimitDecision> timedCallable = TimeLimiter.decorateFutureSupplier(redisTimeLimiter, futureSupplier);
                Callable<RateLimitDecision> protectedCallable = CircuitBreaker.decorateCallable(redisCircuitBreaker, timedCallable);
                
                return protectedCallable.call();
            } catch (Exception ex) {
                // Includes RejectedExecutionException from our bounded queue
                                log.error("Rate limiting bypassed for key '{}' due to Redis unavailability or circuit breaker being open. Falling back to local in-memory rate limiting. Circuit state: {}",
                        cacheKey, redisCircuitBreaker.getState(), ex);
            }
        }

        return checkLocal(cacheKey, authEndpoint);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down RateLimitService executor...");
        redisExecutor.shutdown();
        try {
            if (!redisExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                redisExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            redisExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldUseRedisBackend() {
        return "redis".equalsIgnoreCase(appProperties.getRateLimit().getBackend())
                && redisTemplateProvider.getIfAvailable() != null;
    }

    private RateLimitDecision checkLocal(String cacheKey, boolean authEndpoint) {
        Bucket bucket = localBuckets.get(cacheKey, key -> createBucket(authEndpoint));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        long retryAfterSeconds = probe.isConsumed()
                ? 0
                : Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);

        return new RateLimitDecision(
                probe.isConsumed(),
                probe.getRemainingTokens(),
                retryAfterSeconds);
    }

    private RateLimitDecision checkRedis(String cacheKey, boolean authEndpoint) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getObject();
        AppProperties.RateLimit config = rateLimitConfig(authEndpoint);
        long windowSeconds = authEndpoint
                ? config.getAuthRefillSeconds()
                : config.getRefillSeconds();
        Long current = redisTemplate.execute(
                incrementScript,
                List.of(cacheKey),
                String.valueOf(windowSeconds));

        if (current == null) {
            throw new IllegalStateException("Redis script returned null");
        }

        long capacity = authEndpoint
                ? config.getAuthCapacity()
                : config.getCapacity();
        long remaining = Math.max(capacity - current, 0);

        if (current <= capacity) {
            return new RateLimitDecision(true, remaining, 0);
        }

        Long ttl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
        long retryAfterSeconds = ttl != null && ttl > 0 ? ttl : windowSeconds;
        return new RateLimitDecision(false, 0, retryAfterSeconds);
    }

    private Bucket createBucket(boolean authEndpoint) {
        AppProperties.RateLimit config = rateLimitConfig(authEndpoint);
        int capacity = authEndpoint ? config.getAuthCapacity() : config.getCapacity();
        int refillTokens = authEndpoint ? config.getAuthRefillTokens() : config.getRefillTokens();
        int refillSeconds = authEndpoint ? config.getAuthRefillSeconds() : config.getRefillSeconds();

        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofSeconds(refillSeconds))
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private AppProperties.RateLimit rateLimitConfig(boolean authEndpoint) {
        return appProperties.getRateLimit();
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "anonymous";
        }
        return key.trim().toLowerCase();
    }

    public record RateLimitDecision(boolean allowed, long remainingTokens, long retryAfterSeconds) {
    }
}
