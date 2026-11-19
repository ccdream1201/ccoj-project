-- 创建数据库
CREATE DATABASE IF NOT EXISTS ccoj
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE ccoj;

-- 创建数据库表

-- 用户表

CREATE TABLE IF NOT EXISTS oj_user
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    account       VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    nickname      VARCHAR(64)  NULL COMMENT '昵称',
    avatar_url    VARCHAR(512)          DEFAULT NULL COMMENT '头像地址',
    profile       VARCHAR(1000)         DEFAULT NULL COMMENT '个人简介',
    phone         VARCHAR(32)           DEFAULT NULL COMMENT '手机号',
    email         VARCHAR(254)          DEFAULT NULL COMMENT '邮箱',

    user_role     VARCHAR(20)  NOT NULL DEFAULT 'USER'
        COMMENT '角色：USER普通用户，ADMIN管理员',

    status        TINYINT      NOT NULL DEFAULT 0
        COMMENT '账号状态：0正常，1禁用',

    is_deleted    TINYINT      NOT NULL DEFAULT 0
        COMMENT '逻辑删除：0未删除，1已删除',

    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT '创建时间',

    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account (account)
) ENGINE = InnoDB COMMENT = '用户表';

-- 题目表
CREATE TABLE IF NOT EXISTS problem
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '题目ID',

    title            VARCHAR(200) NOT NULL COMMENT '题目标题',
    description      MEDIUMTEXT   NOT NULL COMMENT '题目描述',
    difficulty       TINYINT      NOT NULL DEFAULT 1 COMMENT '难度：1简单，2中等，3困难',

    samples          JSON         NOT NULL COMMENT '公开样例，数组元素包含input和output',

    judge_cases      JSON         NOT NULL COMMENT '判题用例，数组元素包含input和output，不向普通用户公开',

    judge_config     JSON         NOT NULL COMMENT '判题限制：timeLimitMs单用例时间限制，memoryLimitMb内存限制',

    solution         MEDIUMTEXT COMMENT '官方题解，按权限公开',

    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '发布状态：0草稿，1已发布，2已下架',

    like_count       BIGINT       NOT NULL DEFAULT 0 COMMENT '点赞人数',
    favorite_count   BIGINT       NOT NULL DEFAULT 0 COMMENT '收藏人数',
    submission_count BIGINT       NOT NULL DEFAULT 0 COMMENT '提交次数',
    accepted_count   BIGINT       NOT NULL DEFAULT 0 COMMENT '通过的提交次数',

    created_by       BIGINT       NOT NULL COMMENT '创建者用户ID',

    is_deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_problem_list (is_deleted, status, created_at, id),
    KEY idx_problem_creator (created_by),
    CONSTRAINT fk_problem_creator FOREIGN KEY (created_by) REFERENCES oj_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB COMMENT = '题目表';

-- 用户提交表
-- 3. 用户提交记录表
CREATE TABLE IF NOT EXISTS submission
(
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '提交ID',

    problem_id        BIGINT      NOT NULL COMMENT '题目ID',
    user_id           BIGINT      NOT NULL COMMENT '提交者用户ID',

    language          VARCHAR(32) NOT NULL COMMENT '语言标识，例如JAVA、CPP、PYTHON',

    source_code       MEDIUMTEXT  NOT NULL COMMENT '提交的源代码',

    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '判题状态：PENDING、RUNNING、ACCEPTED、WRONG_ANSWER、COMPILE_ERROR、RUNTIME_ERROR、TIME_LIMIT、MEMORY_LIMIT、OUTPUT_LIMIT、SYSTEM_FAILED',

    judge_attempt     INT         NOT NULL DEFAULT 0 COMMENT '判题执行轮次，从 0 开始',
    retry_at          DATETIME(3) NULL COMMENT '待发布重试的最早时间',

    pending_failure_reason VARCHAR(32) NULL COMMENT '待发布失败通知的原因',

    judge_info        JSON                 DEFAULT NULL COMMENT '判题配置，JSON对象包括最大耗时、最大内存占用',

    passed_case_count INT         NOT NULL DEFAULT 0 COMMENT '通过用例数',
    total_case_count  INT         NOT NULL DEFAULT 0 COMMENT '本次判题用例总数',

    compile_output    TEXT COMMENT '编译器输出，业务侧限制长度',

    is_deleted        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交创建时间',

    updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    PRIMARY KEY (id),

    KEY idx_submission_user (user_id, is_deleted, created_at, id),
    KEY idx_submission_problem (problem_id, is_deleted, created_at, id),
    KEY idx_submission_retry (status, is_deleted, retry_at, id),
    KEY idx_submission_failure_notice (is_deleted, pending_failure_reason, id),
    KEY idx_submission_pending (status, is_deleted, created_at, id),

    CONSTRAINT fk_submission_user
        FOREIGN KEY (user_id) REFERENCES oj_user (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT fk_submission_problem
        FOREIGN KEY (problem_id) REFERENCES problem (id)
            ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB COMMENT = '用户提交记录表';