package com.eat.controller;

import com.eat.common.GlobalExceptionHandler;
import com.eat.dto.request.LoginRequest;
import com.eat.dto.request.PreferenceUpdateRequest;
import com.eat.dto.request.SendCodeRequest;
import com.eat.dto.response.LoginResponse;
import com.eat.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("UserController 单元测试")
class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/user/send-code")
    class SendCode {

        @Test
        @DisplayName("正常发送验证码返回200")
        void shouldReturn200() throws Exception {
            SendCodeRequest req = new SendCodeRequest();
            req.setPhone("13812341234");
            doNothing().when(userService).sendCode("13812341234");

            mockMvc.perform(post("/api/v1/user/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("手机号为空返回400")
        void shouldReturn400ForBlankPhone() throws Exception {
            SendCodeRequest req = new SendCodeRequest();
            req.setPhone("");

            mockMvc.perform(post("/api/v1/user/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("手机号格式错误返回400")
        void shouldReturn400ForInvalidPhone() throws Exception {
            SendCodeRequest req = new SendCodeRequest();
            req.setPhone("1234");

            mockMvc.perform(post("/api/v1/user/send-code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/user/login")
    class Login {

        @Test
        @DisplayName("正常登录返回200和token")
        void shouldReturnToken() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setPhone("13812341234");
            req.setCode("123456");

            LoginResponse resp = new LoginResponse("jwt_xxx", 1L, "138****1234", "2026-06-15T12:00:00");
            when(userService.login("13812341234", "123456")).thenReturn(resp);

            mockMvc.perform(post("/api/v1/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("jwt_xxx"))
                    .andExpect(jsonPath("$.data.userId").value(1));
        }

        @Test
        @DisplayName("验证码错误返回400")
        void shouldReturn400ForWrongCode() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setPhone("13812341234");
            req.setCode("wrong");
            when(userService.login("13812341234", "wrong"))
                    .thenThrow(new IllegalArgumentException("验证码错误或已过期"));

            mockMvc.perform(post("/api/v1/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/user/profile")
    class Profile {

        @Test
        @DisplayName("获取用户资料返回200")
        void shouldReturnProfile() throws Exception {
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("userId", 1L);
            profile.put("phone", "138****1234");
            profile.put("totalUsage", 50L);
            profile.put("totalFavorites", 10);
            when(userService.getProfile(1L)).thenReturn(profile);

            mockMvc.perform(get("/api/v1/user/profile")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.phone").value("138****1234"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/user/preference")
    class Preference {

        @Test
        @DisplayName("获取偏好返回200")
        void shouldReturnPreference() throws Exception {
            Map<String, Object> pref = new LinkedHashMap<>();
            pref.put("taste", "spicy");
            pref.put("taboos", List.of("seafood"));
            pref.put("goal", "diet");
            pref.put("scene", "quick");
            when(userService.getPreference(1L)).thenReturn(pref);

            mockMvc.perform(get("/api/v1/user/preference")
                            .requestAttr("userId", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.taste").value("spicy"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/user/preference")
    class UpdatePreference {

        @Test
        @DisplayName("更新偏好返回200")
        void shouldUpdatePreference() throws Exception {
            PreferenceUpdateRequest req = new PreferenceUpdateRequest();
            req.setTaste("spicy");
            req.setTaboos(List.of("seafood"));
            req.setGoal("diet");
            req.setScene("quick");
            doNothing().when(userService).updatePreference(eq(1L), any());

            mockMvc.perform(put("/api/v1/user/preference")
                            .requestAttr("userId", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }
}
