package com.eat.controller;

import com.eat.common.GlobalExceptionHandler;
import com.eat.dto.request.MealGenerateRequest;
import com.eat.dto.request.MealRegenerateRequest;
import com.eat.dto.response.MealPlanResponse;
import com.eat.dto.response.MealResultResponse;
import com.eat.service.MealService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("MealController 单元测试")
class MealControllerTest {

    private final MealService mealService = mock(MealService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MealController(mealService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private List<MealPlanResponse> samplePlans() {
        MealPlanResponse a = new MealPlanResponse();
        a.setPlanType("quick");
        a.setTitle("快手早餐");
        a.setItems(List.of("牛奶", "面包", "鸡蛋"));
        a.setEstTime("约5分钟");
        a.setCalorieRange("约350-400千卡");
        return List.of(a);
    }

    @Nested
    @DisplayName("POST /api/v1/meal/generate")
    class Generate {

        @Test
        @DisplayName("SSE流式返回推荐结果 - 验证请求受理")
        void shouldAcceptGenerateRequest() throws Exception {
            MealGenerateRequest req = new MealGenerateRequest();
            req.setPeriod("breakfast");

            MealResultResponse result = new MealResultResponse();
            result.setPeriod("breakfast");
            result.setPlans(samplePlans());
            result.setFromFallback(false);
            when(mealService.generate(eq(1L), any())).thenReturn(result);

            mockMvc.perform(post("/api/v1/meal/generate")
                            .requestAttr("userId", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted());
        }

        @Test
        @DisplayName("缺少时段参数返回400")
        void shouldReturn400WhenPeriodMissing() throws Exception {
            MealGenerateRequest req = new MealGenerateRequest();

            mockMvc.perform(post("/api/v1/meal/generate")
                            .requestAttr("userId", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/meal/fallback")
    class Fallback {

        @Test
        @DisplayName("返回兜底方案200")
        void shouldReturnFallback() throws Exception {
            when(mealService.getFallback()).thenReturn(
                    Map.of("breakfast", samplePlans()));

            mockMvc.perform(get("/api/v1/meal/fallback"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.breakfast").isArray());
        }
    }
}
