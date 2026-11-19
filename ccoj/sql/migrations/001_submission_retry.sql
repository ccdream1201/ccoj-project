-- Run against the schema used by ccoj-problem-service.
-- For an existing database, apply once before upgrading the services.
ALTER TABLE submission
    ADD COLUMN judge_attempt INT NOT NULL DEFAULT 0 COMMENT '判题执行轮次，从 0 开始',
    ADD COLUMN retry_at DATETIME(3) NULL COMMENT '待发布重试的最早时间',
    ADD INDEX idx_submission_retry (status, is_deleted, retry_at, id);
