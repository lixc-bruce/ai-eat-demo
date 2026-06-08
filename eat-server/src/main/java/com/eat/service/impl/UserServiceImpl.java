package com.eat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eat.common.utils.JwtUtil;
import com.eat.common.utils.PhoneUtil;
import com.eat.dto.request.PreferenceUpdateRequest;
import com.eat.dto.response.LoginResponse;
import com.eat.entity.User;
import com.eat.entity.UserPreference;
import com.eat.mapper.QaRecordMapper;
import com.eat.mapper.UserMapper;
import com.eat.mapper.UserPreferenceMapper;
import com.eat.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final QaRecordMapper qaRecordMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;
    private final PhoneUtil phoneUtil;

    private static final Set<String> VALID_TASTES = Set.of("light", "heavy", "sweet_sour", "spicy", "");
    private static final Set<String> VALID_GOALS = Set.of("diet", "bulk", "stomach", "crave", "");
    private static final Set<String> VALID_SCENES = Set.of("quick", "serious", "takeout", "home", "");

    @Override
    public void sendCode(String phone) {
        if (!phoneUtil.isValid(phone)) {
            throw new IllegalArgumentException("手机号格式不正确");
        }

        String cdKey = "sms:cd:" + phone;
        if (redisTemplate.opsForValue().get(cdKey) != null) {
            throw new IllegalArgumentException("发送过于频繁，请稍后再试");
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));

        redisTemplate.opsForValue().set("sms:code:" + phone, code, 240, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(cdKey, "1", 60, TimeUnit.SECONDS);

        log.info("[SMS] phone={} codeSent=true", phoneUtil.mask(phone));
    }

    @Override
    public LoginResponse login(String phone, String code) {
        if (!phoneUtil.isValid(phone)) {
            throw new IllegalArgumentException("手机号格式不正确");
        }

        String cacheKey = "sms:code:" + phone;
        String cachedCode = (String) redisTemplate.opsForValue().get(cacheKey);
        if (cachedCode == null) {
            throw new IllegalArgumentException("验证码已失效，请重新获取");
        }
        if (!cachedCode.equals(code)) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }

        redisTemplate.delete(cacheKey);

        String encryptedPhone = phoneUtil.encrypt(phone);
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, encryptedPhone));

        if (user == null) {
            user = new User();
            user.setPhone(encryptedPhone);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
            log.info("[LOGIN] phone={} action=register userId={}", phoneUtil.mask(phone), user.getId());
        } else {
            log.info("[LOGIN] phone={} action=login userId={}", phoneUtil.mask(phone), user.getId());
        }

        String token = jwtUtil.generate(user.getId(), phone);

        redisTemplate.opsForValue().set(
                "token:user:" + user.getId(), token, 7, TimeUnit.DAYS);

        String expiresAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .plusDays(7).toString();

        return new LoginResponse(token, user.getId(), phoneUtil.mask(phone), expiresAt);
    }

    @Override
    public Map<String, Object> getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        String rawPhone = phoneUtil.decrypt(user.getPhone());

        Long qaCount = qaRecordMapper.selectCount(
                new LambdaQueryWrapper<com.eat.entity.QaRecord>()
                        .eq(com.eat.entity.QaRecord::getUserId, userId));

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("userId", userId);
        profile.put("phone", phoneUtil.mask(rawPhone));
        profile.put("totalUsage", qaCount);
        profile.put("totalFavorites", 0);
        return profile;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPreference(Long userId) {
        Object cache = redisTemplate.opsForValue().get("user:pref:" + userId);
        if (cache instanceof Map) {
            return (Map<String, Object>) cache;
        }

        UserPreference up = userPreferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, userId));

        Map<String, Object> pref = new LinkedHashMap<>();
        if (up != null) {
            pref.put("taste", up.getDefaultTaste());
            pref.put("taboos", parseTaboos(up.getDefaultTaboos()));
            pref.put("goal", up.getDefaultGoal());
            pref.put("scene", up.getDefaultScene());
        } else {
            pref.put("taste", "");
            pref.put("taboos", List.of());
            pref.put("goal", "");
            pref.put("scene", "");
        }
        return pref;
    }

    @Override
    public void updatePreference(Long userId, PreferenceUpdateRequest request) {
        if (request.getTaste() != null && !VALID_TASTES.contains(request.getTaste())) {
            throw new IllegalArgumentException("无效的口味值: " + request.getTaste());
        }
        if (request.getGoal() != null && !VALID_GOALS.contains(request.getGoal())) {
            throw new IllegalArgumentException("无效的需求值: " + request.getGoal());
        }
        if (request.getScene() != null && !VALID_SCENES.contains(request.getScene())) {
            throw new IllegalArgumentException("无效的场景值: " + request.getScene());
        }

        UserPreference existing = userPreferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, userId));

        UserPreference up = new UserPreference();
        up.setUserId(userId);
        up.setDefaultTaste(request.getTaste() != null ? request.getTaste() : "");
        up.setDefaultTaboos(joinTaboos(request.getTaboos()));
        up.setDefaultGoal(request.getGoal() != null ? request.getGoal() : "");
        up.setDefaultScene(request.getScene() != null ? request.getScene() : "");
        up.setUpdatedAt(LocalDateTime.now());

        if (existing != null) {
            up.setId(existing.getId());
            userPreferenceMapper.update(up,
                    new LambdaQueryWrapper<UserPreference>()
                            .eq(UserPreference::getUserId, userId));
        } else {
            userPreferenceMapper.insert(up);
        }

        redisTemplate.delete("user:pref:" + userId);
    }

    private List<String> parseTaboos(String taboos) {
        if (taboos == null || taboos.isEmpty()) return List.of();
        return Arrays.asList(taboos.split(","));
    }

    private String joinTaboos(List<String> taboos) {
        if (taboos == null || taboos.isEmpty()) return "";
        return String.join(",", taboos);
    }
}
