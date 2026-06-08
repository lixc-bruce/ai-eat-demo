package com.eat.service.impl;

import com.eat.dto.response.MealPlanResponse;
import com.eat.service.AiProxyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AiProxyServiceImpl implements AiProxyService {

    private static final Map<String, List<MealPlanResponse>> FALLBACK_MAP = new ConcurrentHashMap<>();

    static {
        FALLBACK_MAP.put("breakfast", List.of(
                buildPlan("quick", "经典快手早餐", List.of("牛奶", "全麦面包", "水煮蛋"), "约5分钟", "约350-400千卡"),
                buildPlan("home", "营养均衡早餐", List.of("豆浆", "包子", "水果沙拉"), "约15分钟", "约450-500千卡"),
                buildPlan("comfort", "暖胃舒心早餐", List.of("燕麦粥", "坚果", "酸奶"), "约10分钟", "约300-350千卡")));
        FALLBACK_MAP.put("lunch", List.of(
                buildPlan("quick", "快手午餐便当", List.of("番茄炒蛋", "清炒时蔬", "米饭"), "约10分钟", "约450-500千卡"),
                buildPlan("home", "家常营养午餐", List.of("鸡胸肉沙拉", "全麦卷饼", "紫菜汤"), "约25分钟", "约550-600千卡"),
                buildPlan("comfort", "解馋午餐双拼", List.of("牛肉面", "凉拌黄瓜", "卤蛋"), "约15分钟", "约500-550千卡")));
        FALLBACK_MAP.put("dinner", List.of(
                buildPlan("quick", "轻食晚餐", List.of("清蒸鱼", "炒青菜", "杂粮饭"), "约15分钟", "约400-450千卡"),
                buildPlan("home", "家常晚餐", List.of("蔬菜豆腐汤", "虾仁蒸蛋", "米饭"), "约30分钟", "约500-550千卡"),
                buildPlan("comfort", "暖心晚餐", List.of("鸡丝凉面", "拍黄瓜", "蛋花汤"), "约20分钟", "约450-500千卡")));
        FALLBACK_MAP.put("snack", List.of(
                buildPlan("quick", "水果拼盘", List.of("苹果", "香蕉", "坚果"), "约3分钟", "约200-250千卡"),
                buildPlan("home", "酸奶燕麦", List.of("酸奶", "燕麦棒", "蜂蜜"), "约5分钟", "约250-300千卡"),
                buildPlan("comfort", "蒸红薯+牛奶", List.of("蒸红薯", "热牛奶", "饼干"), "约10分钟", "约300-350千卡")));
    }

    @Override
    public List<MealPlanResponse> recommend(String period, String taste, List<String> taboos,
                                            String goal, String scene) {
        log.info("[AI] recommend period={} taste={}", period, taste);
        return getFallback(period);
    }

    @Override
    public List<MealPlanResponse> getFallback(String period) {
        return FALLBACK_MAP.getOrDefault(period, FALLBACK_MAP.get("lunch"));
    }

    @Override
    public Map<String, List<MealPlanResponse>> getAllFallbacks() {
        return Map.copyOf(FALLBACK_MAP);
    }

    private static MealPlanResponse buildPlan(String planType, String title,
                                               List<String> items, String estTime, String calorieRange) {
        MealPlanResponse plan = new MealPlanResponse();
        plan.setPlanType(planType);
        plan.setTitle(title);
        plan.setItems(items);
        plan.setEstTime(estTime);
        plan.setCalorieRange(calorieRange);
        return plan;
    }
}
