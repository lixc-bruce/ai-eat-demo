package com.eat.service;

import com.eat.dto.response.MealPlanResponse;

import java.util.List;
import java.util.Map;

public interface AiProxyService {

    List<MealPlanResponse> recommend(String period, String taste, List<String> taboos,
                                     String goal, String scene);

    List<MealPlanResponse> getFallback(String period);

    Map<String, List<MealPlanResponse>> getAllFallbacks();
}
