package com.eat.service;

import com.eat.dto.request.MealGenerateRequest;
import com.eat.dto.request.MealRegenerateRequest;
import com.eat.dto.response.MealPlanResponse;
import com.eat.dto.response.MealResultResponse;
import com.eat.service.impl.MealServiceImpl;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MealService 单元测试")
class MealServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private AiProxyService aiProxyService;

    @InjectMocks
    private MealServiceImpl mealService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private List<MealPlanResponse> samplePlans() {
        MealPlanResponse a = new MealPlanResponse();
        a.setPlanType("quick");
        a.setTitle("快手早餐");
        a.setItems(List.of("牛奶", "面包", "鸡蛋"));
        a.setEstTime("约5分钟");
        a.setCalorieRange("约350-400千卡");

        MealPlanResponse b = new MealPlanResponse();
        b.setPlanType("home");
        b.setTitle("营养早餐");
        b.setItems(List.of("豆浆", "包子", "水果"));
        b.setEstTime("约15分钟");
        b.setCalorieRange("约450-500千卡");

        MealPlanResponse c = new MealPlanResponse();
        c.setPlanType("comfort");
        c.setTitle("暖胃早餐");
        c.setItems(List.of("燕麦粥", "坚果", "酸奶"));
        c.setEstTime("约10分钟");
        c.setCalorieRange("约300-350千卡");

        return List.of(a, b, c);
    }

    @Nested
    @DisplayName("generate - 生成饮食推荐")
    class Generate {

        @Test
        @DisplayName("正常生成3套方案")
        void shouldGenerateThreePlans() {
            MealGenerateRequest req = new MealGenerateRequest();
            req.setPeriod("breakfast");
            req.setTaste("light");
            req.setTaboos(List.of("seafood"));
            req.setGoal("diet");
            req.setScene("quick");

            when(valueOperations.get(anyString())).thenReturn(null); // no cache
            when(valueOperations.get(startsWith("rate:meal:"))).thenReturn(null); // no rate limit
            when(aiProxyService.recommend("breakfast", "light", List.of("seafood"), "diet", "quick"))
                    .thenReturn(samplePlans());

            MealResultResponse result = mealService.generate(1L, req);

            assertThat(result.getPeriod()).isEqualTo("breakfast");
            assertThat(result.getPlans()).hasSize(3);
            assertThat(result.getPlans().get(0).getPlanType()).isEqualTo("quick");
            assertThat(result.isFromFallback()).isFalse();
        }

        @Test
        @DisplayName("缓存命中直接返回")
        void shouldReturnFromCache() {
            MealGenerateRequest req = new MealGenerateRequest();
            req.setPeriod("breakfast");

            when(valueOperations.get(startsWith("ai:cache:"))).thenReturn(
                    "{\"period\":\"breakfast\",\"plans\":[],\"fromFallback\":false}");

            MealResultResponse result = mealService.generate(1L, req);

            verify(aiProxyService, never()).recommend(any(), any(), any(), any(), any());
            assertThat(result.isFromFallback()).isFalse();
        }

        @Test
        @DisplayName("超过每日次数返回错误")
        void shouldRejectWhenDailyLimitExceeded() {
            MealGenerateRequest req = new MealGenerateRequest();
            req.setPeriod("lunch");

            when(valueOperations.get(startsWith("ai:cache:"))).thenReturn(null);
            when(valueOperations.get(startsWith("rate:meal:"))).thenReturn("20");

            assertThatThrownBy(() -> mealService.generate(1L, req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("今日推荐次数已用完");
        }

        @Test
        @DisplayName("AI不可用时返回兜底方案")
        void shouldReturnFallbackWhenAiFails() {
            MealGenerateRequest req = new MealGenerateRequest();
            req.setPeriod("breakfast");

            when(valueOperations.get(startsWith("ai:cache:"))).thenReturn(null);
            when(valueOperations.get(startsWith("rate:meal:"))).thenReturn(null);
            when(aiProxyService.recommend(any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("AI timeout"));
            when(aiProxyService.getFallback("breakfast")).thenReturn(samplePlans());

            MealResultResponse result = mealService.generate(1L, req);

            assertThat(result.isFromFallback()).isTrue();
            assertThat(result.getPlans()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("regenerate - 重新生成方案")
    class Regenerate {

        @Test
        @DisplayName("替换指定类型方案")
        void shouldReplaceSpecificPlan() {
            MealRegenerateRequest req = new MealRegenerateRequest();
            req.setPeriod("lunch");
            req.setExcludePlanType("quick");
            req.setExcludeTitle("快手午餐");

            List<MealPlanResponse> replacement = List.of(samplePlans().get(0));
            when(aiProxyService.recommend(eq("lunch"), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(replacement);

            MealPlanResponse result = mealService.regenerate(1L, req);

            assertThat(result).isNotNull();
            verify(valueOperations, never()).increment(anyString()); // regenerate doesn't count
        }
    }

    @Nested
    @DisplayName("getFallback - 获取兜底方案")
    class GetFallback {

        @Test
        @DisplayName("返回所有时段兜底方案")
        void shouldReturnAllFallbacks() {
            when(aiProxyService.getAllFallbacks()).thenReturn(
                    Map.of("breakfast", samplePlans(), "lunch", samplePlans()));

            Map<String, List<MealPlanResponse>> fallbacks = mealService.getFallback();

            assertThat(fallbacks).containsKeys("breakfast", "lunch");
            assertThat(fallbacks.get("breakfast")).hasSize(3);
        }
    }
}
