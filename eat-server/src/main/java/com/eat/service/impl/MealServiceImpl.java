package com.eat.service.impl;

import com.eat.dto.request.MealGenerateRequest;
import com.eat.dto.request.MealRegenerateRequest;
import com.eat.dto.response.MealPlanResponse;
import com.eat.dto.response.MealResultResponse;
import com.eat.service.AiProxyService;
import com.eat.service.MealService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealServiceImpl implements MealService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AiProxyService aiProxyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public MealResultResponse generate(Long userId, MealGenerateRequest request) {
        String cacheKey = buildCacheKey(request);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof String cachedStr) {
            try {
                return objectMapper.readValue(cachedStr, MealResultResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Cache parse failed, re-generating", e);
            }
        }

        String dailyKey = "rate:meal:" + userId;
        String countStr = (String) redisTemplate.opsForValue().get(dailyKey);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        if (count >= 20) {
            throw new RuntimeException("今日推荐次数已用完，明天再来吧");
        }

        List<MealPlanResponse> plans;
        boolean fromFallback = false;
        try {
            plans = aiProxyService.recommend(
                    request.getPeriod(), request.getTaste(),
                    request.getTaboos(), request.getGoal(), request.getScene());
        } catch (Exception e) {
            log.error("[AI] generate failed, using fallback", e);
            plans = aiProxyService.getFallback(request.getPeriod());
            fromFallback = true;
        }

        long ttl = Duration.between(LocalDateTime.now(),
                LocalDateTime.now().plusDays(1).with(LocalTime.MIN)).getSeconds();
        redisTemplate.opsForValue().increment(dailyKey, 1);
        redisTemplate.expire(dailyKey, ttl, TimeUnit.SECONDS);

        MealResultResponse result = new MealResultResponse();
        result.setPeriod(request.getPeriod());
        result.setPlans(plans);
        result.setFromFallback(fromFallback);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
                    3600, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Cache write failed", e);
        }

        log.info("[MEAL] userId={} period={} fromFallback={}", userId, request.getPeriod(), fromFallback);
        return result;
    }

    @Override
    public MealPlanResponse regenerate(Long userId, MealRegenerateRequest request) {
        List<MealPlanResponse> plans = aiProxyService.recommend(
                request.getPeriod(), request.getTaste(),
                request.getTaboos(), request.getGoal(), request.getScene());
        return plans.isEmpty() ? null : plans.get(0);
    }

    @Override
    public Map<String, List<MealPlanResponse>> getFallback() {
        return aiProxyService.getAllFallbacks();
    }

    private String buildCacheKey(MealGenerateRequest request) {
        String raw = String.format("%s|%s|%s|%s|%s",
                request.getPeriod(),
                request.getTaste() != null ? request.getTaste() : "",
                request.getTaboos() != null ? String.join(",", request.getTaboos()) : "",
                request.getGoal() != null ? request.getGoal() : "",
                request.getScene() != null ? request.getScene() : "");
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(raw.getBytes(StandardCharsets.UTF_8));
            return "ai:cache:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
