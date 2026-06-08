package com.eat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eat_favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String mealPeriod;

    private String planType;

    private String planTitle;

    private String planContent;

    private String planItems;

    private String estTime;

    private String calorieRange;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;
}
