CREATE TABLE IF NOT EXISTS eat_favorite (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL COMMENT '关联用户ID',
    meal_period   VARCHAR(10) NOT NULL COMMENT '用餐时段: breakfast/lunch/dinner/snack',
    plan_type     VARCHAR(20) NOT NULL COMMENT '方案类型: quick/home/comfort',
    plan_title    VARCHAR(100) NOT NULL COMMENT '方案标题',
    plan_content  TEXT NOT NULL COMMENT '方案完整内容JSON',
    plan_items    TEXT COMMENT '菜品列表JSON',
    est_time      VARCHAR(20) COMMENT '预估耗时',
    calorie_range VARCHAR(30) COMMENT '热量区间',
    is_deleted    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除: 0正常 1已删除',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, created_at DESC),
    FOREIGN KEY (user_id) REFERENCES eat_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';
