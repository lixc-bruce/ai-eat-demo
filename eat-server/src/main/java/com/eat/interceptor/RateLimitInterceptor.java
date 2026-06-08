package com.eat.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${eat.rate-limit.meal-per-minute:5}")
    private int mealPerMinute;

    @Value("${eat.rate-limit.qa-per-minute:10}")
    private int qaPerMinute;

    @Value("${eat.rate-limit.sms-per-minute:1}")
    private int smsPerMinute;

    @Value("${eat.rate-limit.meal-daily:20}")
    private int mealDaily;

    @Value("${eat.rate-limit.qa-daily:30}")
    private int qaDaily;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String path = request.getRequestURI();
        String userId = getUserId(request);
        String clientIp = getClientIp(request);

        // 短信验证码限流
        if (path.contains("/user/send-code")) {
            if (!checkSlidingWindow("sms:" + clientIp, smsPerMinute, 60)) {
                response.setStatus(429);
                return false;
            }
            return true;
        }

        // 饮食推荐限流
        if (path.contains("/meal/generate") || path.contains("/meal/regenerate")) {
            if (userId != null) {
                if (!checkDailyLimit("rate:meal:" + userId, mealDaily)) {
                    response.setStatus(429);
                    return false;
                }
            }
            if (!checkSlidingWindow("rate:meal:ip:" + clientIp, mealPerMinute, 60)) {
                response.setStatus(429);
                return false;
            }
        }

        // 问答限流
        if (path.contains("/qa/ask")) {
            if (userId != null) {
                if (!checkDailyLimit("rate:qa:" + userId, qaDaily)) {
                    response.setStatus(429);
                    return false;
                }
            }
            if (!checkSlidingWindow("rate:qa:ip:" + clientIp, qaPerMinute, 60)) {
                response.setStatus(429);
                return false;
            }
        }

        return true;
    }

    private boolean checkSlidingWindow(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;

        String redisKey = "rate:sliding:" + key;
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);
        Long count = redisTemplate.opsForZSet().count(redisKey, windowStart, now);

        if (count != null && count >= limit) {
            return false;
        }

        redisTemplate.opsForZSet().add(redisKey, UUID.randomUUID().toString(), now);
        redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        return true;
    }

    private boolean checkDailyLimit(String key, int limit) {
        String count = (String) redisTemplate.opsForValue().get(key);
        if (count != null && Integer.parseInt(count) >= limit) {
            return false;
        }
        return true;
    }

    private String getUserId(HttpServletRequest request) {
        Long userId = JwtInterceptor.USER_ID_HOLDER.get();
        return userId != null ? userId.toString() : null;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
