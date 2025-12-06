package com.ankitj.RateLimiterDemo.config;

import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${rate.limit.requests:10}")
    private int rateLimitRequests;

    @Value("${rate.limit.window.minutes:1}")
    private int rateLimitWindowMinutes;

    @Bean
    public LettuceBasedProxyManager<byte[]> proxyManager() {
        RedisURI redisURI = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .build();
        
        RedisClient redisClient = RedisClient.create(redisURI);
        
        return LettuceBasedProxyManager.builderFor(redisClient)
                .build();
    }

    public int getRateLimitRequests() {
        return rateLimitRequests;
    }

    public int getRateLimitWindowMinutes() {
        return rateLimitWindowMinutes;
    }
}

