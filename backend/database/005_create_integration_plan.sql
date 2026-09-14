-- 执行前先备份数据库；本脚本只新增方案与版本表，不改现有认知记录。
CREATE TABLE IF NOT EXISTS cognitive_revision (
    user_id BIGINT PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS integration_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    draft_id BIGINT NOT NULL,
    draft_request_id VARCHAR(100) NOT NULL,
    plan_request_id VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL,
    baseline_revision BIGINT NOT NULL,
    confirmed_draft_json LONGTEXT NOT NULL,
    cognitive_update_json LONGTEXT,
    preview_json LONGTEXT,
    summary_json LONGTEXT,
    result_json LONGTEXT,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_integration_plan_user_request (user_id, plan_request_id),
    KEY idx_integration_plan_user_status (user_id, status),
    KEY idx_integration_plan_draft (draft_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 同一份第一次 Draft 可以反复生成预览，但最多只允许一份最终方案真正入库。
CREATE TABLE IF NOT EXISTS draft_commit_guard (
    user_id BIGINT NOT NULL,
    draft_request_id VARCHAR(100) NOT NULL,
    applied_plan_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, draft_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
