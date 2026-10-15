-- 版本：20260920_01；增加冻结 JD/简历事实快照的一次性 AI 专业评分缓存。
-- 前提：MySQL 8，已部署 demand_jd_fact_snapshot 与 talent_resume_fact_snapshot；升级前停止评分 Worker 并完成备份。
-- 数据影响：新增空表，并为既有匹配运行补充 scoring_version；历史运行统一保留为 legacy-deterministic，不冒充 AI 评分。
-- 重跑与部分完成：加列与建表前后均通过当前数据库元数据核对，可在任一步完成后安全接续。
-- 恢复：旧应用可忽略并保留该表；确需删除时先停用评分 Worker、确认无在途租约并完成备份。
SET NAMES utf8mb4;

SET @ai_professional_assessment_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_fact_snapshot' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_fact_snapshot' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_match_run' AND ENGINE='InnoDB')=1,
    'DO 0',
    'SELECT `AI professional assessment upgrade requires JD and resume fact snapshots plus matching_match_run`'
);
PREPARE ai_professional_assessment_stmt FROM @ai_professional_assessment_sql;
EXECUTE ai_professional_assessment_stmt;
DEALLOCATE PREPARE ai_professional_assessment_stmt;

SET @ai_professional_assessment_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_match_run' AND COLUMN_NAME='scoring_version')=0,
    'ALTER TABLE matching_match_run ADD COLUMN scoring_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT ''legacy-deterministic'' COMMENT ''专业评分来源版本，隔离旧规则评分与 AI 评分'' AFTER run_purpose',
    IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='matching_match_run' AND COLUMN_NAME='scoring_version'
       AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=80 AND IS_NULLABLE='NO'
       AND COLUMN_DEFAULT='legacy-deterministic' AND CHARACTER_SET_NAME='ascii'
       AND COLLATION_NAME='ascii_bin')=1,
      'DO 0',
      'SELECT `existing matching_match_run.scoring_version definition is incompatible and has been preserved`')
);
PREPARE ai_professional_assessment_stmt FROM @ai_professional_assessment_sql;
EXECUTE ai_professional_assessment_stmt;
DEALLOCATE PREPARE ai_professional_assessment_stmt;

CREATE TABLE IF NOT EXISTS `matching_ai_professional_assessment`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    cache_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冻结事实、提示词和结构版本缓存键',
    jd_fact_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'JD 事实快照 ID',
    resume_fact_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '简历事实快照 ID',
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评分提示词版本',
    schema_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评分结果结构版本',
    provider VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型供应商',
    model VARCHAR(128) NULL COMMENT '模型名称',
    evaluated_on DATE NOT NULL COMMENT '评分业务日期',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '处理状态',
    claim_token CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前领取令牌',
    lease_until TIMESTAMP(6) NULL COMMENT '当前领取租约截止时间',
    result_json JSON NULL COMMENT 'AI 五维评分、理由、证据及置信度',
    input_tokens INT NOT NULL DEFAULT 0 COMMENT '输入 Token 数',
    output_tokens INT NOT NULL DEFAULT 0 COMMENT '输出 Token 数',
    duration_millis BIGINT NOT NULL DEFAULT 0 COMMENT '模型调用耗时毫秒',
    error_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '稳定错误编码',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记，历史评分默认保留',
    `ext` TEXT NULL COMMENT 'JSON 扩展信息',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_matching_ai_professional_cache UNIQUE (cache_key),
    CONSTRAINT fk_matching_ai_professional_jd_fact FOREIGN KEY (jd_fact_snapshot_id) REFERENCES demand_jd_fact_snapshot(id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_ai_professional_resume_fact FOREIGN KEY (resume_fact_snapshot_id) REFERENCES talent_resume_fact_snapshot(id) ON DELETE RESTRICT,
    CONSTRAINT ck_matching_ai_professional_status CHECK (status IN ('PROCESSING','SUCCEEDED','FAILED')),
    CONSTRAINT ck_matching_ai_professional_usage CHECK (input_tokens >= 0 AND output_tokens >= 0 AND duration_millis >= 0),
    CONSTRAINT ck_matching_ai_professional_state CHECK (
      (status='PROCESSING' AND claim_token IS NOT NULL AND lease_until IS NOT NULL AND result_json IS NULL)
      OR (status='SUCCEEDED' AND claim_token IS NULL AND lease_until IS NULL AND result_json IS NOT NULL
          AND provider IS NOT NULL AND model IS NOT NULL AND error_code IS NULL)
      OR (status='FAILED' AND claim_token IS NULL AND lease_until IS NULL AND result_json IS NULL AND error_code IS NOT NULL)),
    INDEX ix_matching_ai_professional_claim (status,lease_until,update_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结事实快照的可复用 AI 专业评分';

SET @ai_professional_assessment_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_match_run' AND COLUMN_NAME='scoring_version'
     AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=80 AND IS_NULLABLE='NO'
     AND COLUMN_DEFAULT='legacy-deterministic' AND CHARACTER_SET_NAME='ascii'
     AND COLLATION_NAME='ascii_bin')=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_ai_professional_assessment' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_ai_professional_assessment')=27 AND
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='matching_ai_professional_assessment'
     AND INDEX_NAME='uk_matching_ai_professional_cache' AND NON_UNIQUE=0)='cache_key' AND
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='matching_ai_professional_assessment'
     AND INDEX_NAME='ix_matching_ai_professional_claim' AND NON_UNIQUE=1)='status,lease_until,update_time,id' AND
    (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_ai_professional_assessment'
     AND CONSTRAINT_NAME IN ('fk_matching_ai_professional_jd_fact','fk_matching_ai_professional_resume_fact')
     AND DELETE_RULE='RESTRICT')=2 AND
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_ai_professional_assessment' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'
     AND CONSTRAINT_NAME IN ('ck_matching_ai_professional_status','ck_matching_ai_professional_usage',
       'ck_matching_ai_professional_state'))=3,
    'DO 0',
    'SELECT `existing matching_ai_professional_assessment definition is incompatible and has been preserved`'
);
PREPARE ai_professional_assessment_stmt FROM @ai_professional_assessment_sql;
EXECUTE ai_professional_assessment_stmt;
DEALLOCATE PREPARE ai_professional_assessment_stmt;
SET @ai_professional_assessment_sql = NULL;
