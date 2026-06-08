package com.eat.service;

import com.eat.dto.request.MealGenerateRequest;
import com.eat.dto.request.MealRegenerateRequest;
import com.eat.dto.response.MealPlanResponse;
import com.eat.dto.response.MealResultResponse;

import java.util.List;
import java.util.Map;

public interface MealService {

    MealResultResponse generate(Long userId, MealGenerateRequest request);

    MealPlanResponse regenerate(Long userId, MealRegenerateRequest request);

    Map<String, List<MealPlanResponse>> getFallback();
}
