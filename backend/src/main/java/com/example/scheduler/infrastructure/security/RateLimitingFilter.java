package com.example.scheduler.infrastructure.security;

import com.example.scheduler.infrastructure.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AppProperties properties;
    private final Clock clock;

    public RateLimitingFilter(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader("X-Forwarded-For");
        if (key == null || key.isBlank()) {
            key = request.getRemoteAddr();
        }

        TokenBucket bucket = buckets.computeIfAbsent(key, ignored -> new TokenBucket(
                properties.security().rateLimit().capacity(),
                properties.security().rateLimit().refillPerMinute(),
                clock.instant()
        ));

        if (!bucket.tryConsume(clock.instant())) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static final class TokenBucket {
        private final int capacity;
        private final int refillPerMinute;
        private double tokens;
        private Instant lastRefillAt;

        private TokenBucket(int capacity, int refillPerMinute, Instant now) {
            this.capacity = capacity;
            this.refillPerMinute = refillPerMinute;
            this.tokens = capacity;
            this.lastRefillAt = now;
        }

        synchronized boolean tryConsume(Instant now) {
            long elapsedMillis = Math.max(0, now.toEpochMilli() - lastRefillAt.toEpochMilli());
            if (elapsedMillis > 0) {
                double refill = (elapsedMillis / 60_000.0) * refillPerMinute;
                tokens = Math.min(capacity, tokens + refill);
                lastRefillAt = now;
            }
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }
}
