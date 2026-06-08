package com.eat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class MealGenerateRequest {

    @NotBlank(message = "用餐时段不能为空")
    private String period;

    private String taste;

    private List<String> taboos;

    private String goal;

    private String scene;
}
