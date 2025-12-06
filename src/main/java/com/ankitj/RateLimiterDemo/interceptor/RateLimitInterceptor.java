package com.ankitj.RateLimiterDemo.interceptor;

import com.ankitj.RateLimiterDemo.config.RateLimitConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitConfig rateLimitConfig;

    public RateLimitInterceptor(ProxyManager<byte[]> proxyManager, RateLimitConfig rateLimitConfig) {
        this.proxyManager = proxyManager;
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIpAddress(request);
        byte[] ipBytes = clientIp.getBytes();
        
        Supplier<BucketConfiguration> configSupplier = () -> {
            Bandwidth limit = Bandwidth.builder()
                    .capacity(rateLimitConfig.getRateLimitRequests())
                    .refillIntervally(rateLimitConfig.getRateLimitRequests(), Duration.ofMinutes(rateLimitConfig.getRateLimitWindowMinutes()))
                    .build();
            return BucketConfiguration.builder()
                    .addLimit(limit)
                    .build();
        };

        Bucket bucket = proxyManager.builder()
                .build(ipBytes, configSupplier);

        if (bucket.tryConsume(1)) {
            long availableTokens = bucket.getAvailableTokens();
            response.setHeader("X-RateLimit-Remaining", String.valueOf(availableTokens));
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitConfig.getRateLimitRequests()));
            return true;
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(rateLimitConfig.getRateLimitWindowMinutes() * 60));
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitConfig.getRateLimitRequests()));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}");
            return false;
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}

