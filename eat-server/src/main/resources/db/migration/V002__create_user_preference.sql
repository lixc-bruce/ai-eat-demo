CREATE TABLE IF NOT EXISTS eat_user_preference (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL UNIQUE COMMENT '关联用户ID',
    default_taste   VARCHAR(50)  DEFAULT '' COMMENT '默认口味: light/heavy/sweet_sour/spicy/""',
    default_taboos  VARCHAR(255) DEFAULT '' COMMENT '默认忌口, 逗号分隔',
    default_goal    VARCHAR(50)  DEFAULT '' COMMENT '默认目标: diet/bulk/stomach/crave/""',
    default_scene   VARCHAR(50)  DEFAULT '' COMMENT '默认场景: quick/serious/takeout/home/""',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES eat_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户偏好表';
