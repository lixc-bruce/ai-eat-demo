package com.eat.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class MealResultResponse {

    private String period;

    private List<MealPlanResponse> plans;

    private boolean fromFallback;
}
