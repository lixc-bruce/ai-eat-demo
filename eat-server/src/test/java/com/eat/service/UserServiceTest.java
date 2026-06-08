package com.eat.service;

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
import com.eat.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 单元测试")
class UserServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserPreferenceMapper userPreferenceMapper;
    @Mock
    private QaRecordMapper qaRecordMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PhoneUtil phoneUtil;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("sendCode - 发送短信验证码")
    class SendCode {

        @Test
        @DisplayName("正常发送验证码")
        void shouldSendCodeSuccessfully() {
            when(valueOperations.get("sms:cd:13812341234")).thenReturn(null);


            userService.sendCode("13812341234");

            verify(valueOperations).set(
                    eq("sms:code:13812341234"), anyString(),
                    eq(240L), eq(TimeUnit.SECONDS));
            verify(valueOperations).set(
                    eq("sms:cd:13812341234"), eq("1"),
                    eq(60L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("冷却期内发送返回错误")
        void shouldRejectWhenInCooldown() {
            when(valueOperations.get("sms:cd:13812341234")).thenReturn("1");


            assertThatThrownBy(() -> userService.sendCode("13812341234"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("稍后再试");
        }

        @Test
        @DisplayName("无效手机号返回错误")
        void shouldRejectInvalidPhone() {


            assertThatThrownBy(() -> userService.sendCode("1234"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("手机号格式不正确");
        }
    }

    @Nested
    @DisplayName("login - 验证码登录")
    class Login {

        @Test
        @DisplayName("新用户首次登录自动注册")
        void shouldAutoRegisterNewUser() {
            String phone = "13812341234";
            when(valueOperations.get("sms:code:" + phone)).thenReturn("123456");
            when(phoneUtil.encrypt(phone)).thenReturn("encrypted_phone");
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            doAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return 1;
            }).when(userMapper).insert(any(User.class));
            when(jwtUtil.generate(1L, phone)).thenReturn("jwt_token_xxx");
            when(phoneUtil.mask(phone)).thenReturn("138****1234");

            LoginResponse resp = userService.login(phone, "123456");

            verify(userMapper).insert(any(User.class));
            verify(redisTemplate).delete("sms:code:" + phone);
            assertThat(resp.getToken()).isEqualTo("jwt_token_xxx");
            assertThat(resp.getPhone()).isEqualTo("138****1234");
        }

        @Test
        @DisplayName("已注册用户登录成功")
        void shouldLoginExistingUser() {
            String phone = "13812341234";
            User existingUser = new User();
            existingUser.setId(1L);
            existingUser.setPhone("encrypted_phone");

            when(valueOperations.get("sms:code:" + phone)).thenReturn("123456");
            when(phoneUtil.encrypt(phone)).thenReturn("encrypted_phone");
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);
            when(jwtUtil.generate(1L, phone)).thenReturn("jwt_token_xxx");
            when(phoneUtil.mask(phone)).thenReturn("138****1234");

            LoginResponse resp = userService.login(phone, "123456");

            verify(userMapper, never()).insert(any());
            assertThat(resp.getUserId()).isEqualTo(1L);
            assertThat(resp.getToken()).isEqualTo("jwt_token_xxx");
        }

        @Test
        @DisplayName("验证码错误返回异常")
        void shouldRejectWrongCode() {
            String phone = "13812341234";
            when(valueOperations.get("sms:code:" + phone)).thenReturn("654321");

            assertThatThrownBy(() -> userService.login(phone, "123456"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("验证码错误");
        }

        @Test
        @DisplayName("验证码过期返回异常")
        void shouldRejectExpiredCode() {
            String phone = "13812341234";
            when(valueOperations.get("sms:code:" + phone)).thenReturn(null);

            assertThatThrownBy(() -> userService.login(phone, "123456"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("验证码已失效");
        }
    }

    @Nested
    @DisplayName("getProfile - 获取个人资料")
    class GetProfile {

        @Test
        @DisplayName("正常获取用户资料")
        void shouldReturnProfile() {
            User user = new User();
            user.setId(1L);
            user.setPhone("encrypted_phone");

            when(userMapper.selectById(1L)).thenReturn(user);
            when(phoneUtil.decrypt("encrypted_phone")).thenReturn("13812341234");
            when(phoneUtil.mask("13812341234")).thenReturn("138****1234");
            when(qaRecordMapper.selectCount(any())).thenReturn(50L);

            Map<String, Object> profile = userService.getProfile(1L);

            assertThat(profile.get("userId")).isEqualTo(1L);
            assertThat(profile.get("phone")).isEqualTo("138****1234");
            assertThat(profile.get("totalUsage")).isEqualTo(50L);
        }

        @Test
        @DisplayName("用户不存在返回异常")
        void shouldThrowWhenUserNotFound() {
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.getProfile(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户不存在");
        }
    }

    @Nested
    @DisplayName("getPreference - 获取用户偏好")
    class GetPreference {

        @Test
        @DisplayName("缓存命中直接返回")
        void shouldReturnFromCache() {
            when(valueOperations.get("user:pref:1")).thenReturn(
                    Map.of("taste", "spicy", "taboos", List.of("seafood"), "goal", "diet", "scene", "quick"));

            Map<String, Object> pref = userService.getPreference(1L);

            assertThat(pref.get("taste")).isEqualTo("spicy");
            assertThat(pref.get("goal")).isEqualTo("diet");
        }

        @Test
        @DisplayName("缓存未命中查数据库")
        void shouldFallbackToDatabase() {
            UserPreference up = new UserPreference();
            up.setDefaultTaste("light");
            up.setDefaultTaboos("");
            up.setDefaultGoal("");
            up.setDefaultScene("");

            when(valueOperations.get("user:pref:1")).thenReturn(null);
            when(userPreferenceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(up);

            Map<String, Object> pref = userService.getPreference(1L);

            assertThat(pref.get("taste")).isEqualTo("light");
        }

        @Test
        @DisplayName("无偏好记录返回空默认值")
        void shouldReturnDefaultsWhenNoPreference() {
            when(valueOperations.get("user:pref:1")).thenReturn(null);
            when(userPreferenceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Map<String, Object> pref = userService.getPreference(1L);

            assertThat(pref.get("taste")).isEqualTo("");
            assertThat(pref.get("taboos")).isEqualTo(List.of());
        }
    }

    @Nested
    @DisplayName("updatePreference - 更新用户偏好")
    class UpdatePreference {

        @Test
        @DisplayName("正常更新偏好")
        void shouldUpdatePreference() {
            PreferenceUpdateRequest req = new PreferenceUpdateRequest();
            req.setTaste("spicy");
            req.setTaboos(List.of("seafood", "lactose"));
            req.setGoal("diet");
            req.setScene("quick");

            UserPreference existing = new UserPreference();
            existing.setId(1L);
            existing.setUserId(1L);
            when(userPreferenceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            userService.updatePreference(1L, req);

            verify(userPreferenceMapper).update(any(UserPreference.class), any());
            verify(redisTemplate).delete("user:pref:1");
        }

        @Test
        @DisplayName("无效口味值返回错误")
        void shouldRejectInvalidTaste() {
            PreferenceUpdateRequest req = new PreferenceUpdateRequest();
            req.setTaste("unknown_flavor");

            assertThatThrownBy(() -> userService.updatePreference(1L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("无效的口味值");
        }
    }
}
