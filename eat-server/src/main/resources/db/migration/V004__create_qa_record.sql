CREATE TABLE IF NOT EXISTS eat_qa_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL COMMENT '关联用户ID',
    session_id    VARCHAR(64) NOT NULL COMMENT '会话ID (UUID)',
    question      VARCHAR(500) NOT NULL COMMENT '用户问题',
    answer        TEXT NOT NULL COMMENT 'AI回复',
    is_sensitive  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '敏感词拦截: 0正常 1拦截',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_session (user_id, session_id),
    INDEX idx_user_time (user_id, created_at DESC),
    FOREIGN KEY (user_id) REFERENCES eat_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答记录表';
