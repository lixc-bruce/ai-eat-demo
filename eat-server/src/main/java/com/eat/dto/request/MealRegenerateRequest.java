package com.eat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class MealRegenerateRequest {

    @NotBlank(message = "用餐时段不能为空")
    private String period;

    @NotBlank(message = "要替换的方案类型不能为空")
    private String excludePlanType;

    @NotBlank(message = "要排除的方案标题不能为空")
    private String excludeTitle;

    private String taste;

    private List<String> taboos;

    private String goal;

    private String scene;
}
