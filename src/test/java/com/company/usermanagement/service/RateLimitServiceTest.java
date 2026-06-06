package com.company.usermanagement.service;

import com.company.usermanagement.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        AppProperties.RateLimit rateLimitConfig = new AppProperties.RateLimit();
        rateLimitConfig.setCapacity(10);
        rateLimitConfig.setRefillTokens(10);
        rateLimitConfig.setRefillSeconds(60);
        rateLimitConfig.setAuthCapacity(5);
        rateLimitConfig.setAuthRefillTokens(5);
        rateLimitConfig.setAuthRefillSeconds(60);
        
        when(appProperties.getRateLimit()).thenReturn(rateLimitConfig);
        
        rateLimitService = new RateLimitService(appProperties, redisTemplateProvider);
    }

    @Test
    void check_WithLocalBackend_ShouldWork() {
        appProperties.getRateLimit().setBackend("local");

        RateLimitService.RateLimitDecision decision = rateLimitService.check("test-key", false);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remainingTokens()).isEqualTo(9);
    }

    @SuppressWarnings("unchecked")
    @Test
    void check_WithRedisBackend_ShouldWork() {
        appProperties.getRateLimit().setBackend("redis");
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplateProvider.getObject()).thenReturn(redisTemplate);
        
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        RateLimitService.RateLimitDecision decision = rateLimitService.check("test-key", false);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remainingTokens()).isEqualTo(9);
    }

    @SuppressWarnings("unchecked")
    @Test
    void check_WithRedisBackend_WhenExceeded_ShouldReturnRetryAfter() {
        appProperties.getRateLimit().setBackend("redis");
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplateProvider.getObject()).thenReturn(redisTemplate);
        
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(11L);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(45L);

        RateLimitService.RateLimitDecision decision = rateLimitService.check("test-key", false);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remainingTokens()).isZero();
        assertThat(decision.retryAfterSeconds()).isEqualTo(45L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void check_WithRedisBackend_WhenRedisFails_ShouldFallbackToLocal() {
        appProperties.getRateLimit().setBackend("redis");
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplateProvider.getObject()).thenReturn(redisTemplate);
        
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenThrow(new RuntimeException("Redis down"));

        RateLimitService.RateLimitDecision decision = rateLimitService.check("test-key", false);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remainingTokens()).isEqualTo(9);
    }

    @Test
    void check_WithAuthEndpoint_ShouldUseAuthLimits() {
        appProperties.getRateLimit().setBackend("local");

        // Use 5 tokens (auth capacity is 5)
        for (int i = 0; i < 5; i++) {
            RateLimitService.RateLimitDecision decision = rateLimitService.check("auth-key", true);
            assertThat(decision.allowed()).isTrue();
        }

        RateLimitService.RateLimitDecision decision = rateLimitService.check("auth-key", true);
        assertThat(decision.allowed()).isFalse();
    }
}
