package com.eat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eat_user_preference")
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String defaultTaste;

    private String defaultTaboos;

    private String defaultGoal;

    private String defaultScene;

    private LocalDateTime updatedAt;
}
