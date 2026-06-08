package com.eat.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class MealPlanResponse {

    private String planType;

    private String title;

    private List<String> items;

    private String estTime;

    private String calorieRange;
}
