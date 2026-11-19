-- Run once after 001_submission_retry.sql on an existing schema.
ALTER TABLE submission
    ADD COLUMN pending_failure_reason VARCHAR(32) NULL COMMENT '待发布失败通知的原因',
    ADD INDEX idx_submission_failure_notice (is_deleted, pending_failure_reason, id);
