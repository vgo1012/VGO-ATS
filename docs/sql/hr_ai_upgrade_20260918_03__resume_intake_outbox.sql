-- 版本：20260918_03；为简历收件独立状态机增加跨实例持久唤醒 Outbox。
-- 前提：MySQL 8，已部署 talent_resume_intake_processing；升级前停止旧版收件 Worker 并完成备份。
-- 数据影响：仅新增空表，不创建通用异步影子任务，不改写既有处理状态或简历数据。
-- 重跑与部分完成：建表前后均核对当前数据库元数据，CREATE TABLE IF NOT EXISTS 可安全接续。
-- 恢复：旧应用可忽略并保留该表；确需删除时先停用消息发布并确认无待发布记录后备份。
SET NAMES utf8mb4;

SET @resume_intake_outbox_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing' AND ENGINE='InnoDB')=1,
    'DO 0',
    'SELECT `resume intake outbox upgrade requires talent_resume_intake_processing`'
);
PREPARE resume_intake_outbox_stmt FROM @resume_intake_outbox_sql;
EXECUTE resume_intake_outbox_stmt;
DEALLOCATE PREPARE resume_intake_outbox_stmt;

CREATE TABLE IF NOT EXISTS `talent_resume_intake_outbox`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '领域通知事件 ID',
    processing_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '共享处理 ID',
    processing_version INT NOT NULL COMMENT '产生通知时的处理版本',
    event_type VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件协议类型',
    event_version INT NOT NULL COMMENT '事件协议版本',
    task_type VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '唤醒类型及路由键',
    occurred_at TIMESTAMP(6) NOT NULL COMMENT '业务状态提交时间',
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '安全链路关联 ID',
    actor_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务操作者快照',
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发布状态',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '发布领取次数',
    max_attempts INT NOT NULL DEFAULT 5 COMMENT '最大发布次数',
    next_retry_at TIMESTAMP(6) NULL COMMENT '下次允许发布时间',
    lease_owner CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '发布租约标识',
    lease_expires_at TIMESTAMP(6) NULL COMMENT '发布租约到期时间',
    published_at TIMESTAMP(6) NULL COMMENT 'Broker 确认时间',
    failure_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '稳定发布失败编码',
    ext TEXT NULL COMMENT 'JSON 扩展信息', remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_intake_outbox_binding UNIQUE (processing_id, processing_version, task_type),
    INDEX ix_talent_intake_outbox_claim (status, next_retry_at, lease_expires_at, occurred_at, id),
    CONSTRAINT fk_talent_intake_outbox_processing FOREIGN KEY (processing_id) REFERENCES talent_resume_intake_processing(id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_intake_outbox_status CHECK (status IN ('PENDING','RUNNING','PUBLISHED','FAILED_RETRYABLE','FAILED_FINAL')),
    CONSTRAINT ck_talent_intake_outbox_attempt CHECK (attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts),
    CONSTRAINT ck_talent_intake_outbox_protocol CHECK (event_version = 1 AND task_type IN ('RESUME_INTAKE_PARSE','RESUME_INTAKE_COMPLETE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历收件处理领域 Outbox';

SET @resume_intake_outbox_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_outbox' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_outbox')=26 AND
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake_outbox'
     AND INDEX_NAME='uk_talent_intake_outbox_binding' AND NON_UNIQUE=0)
     ='processing_id,processing_version,task_type' AND
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake_outbox'
     AND INDEX_NAME='ix_talent_intake_outbox_claim' AND NON_UNIQUE=1)
     ='status,next_retry_at,lease_expires_at,occurred_at,id' AND
    (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_outbox' AND CONSTRAINT_NAME='fk_talent_intake_outbox_processing'
     AND REFERENCED_TABLE_NAME='talent_resume_intake_processing' AND DELETE_RULE='RESTRICT')=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_outbox' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'
     AND CONSTRAINT_NAME IN ('ck_talent_intake_outbox_status','ck_talent_intake_outbox_attempt',
       'ck_talent_intake_outbox_protocol'))=3,
    'DO 0',
    'SELECT `existing talent_resume_intake_outbox definition is incompatible and has been preserved`'
);
PREPARE resume_intake_outbox_stmt FROM @resume_intake_outbox_sql;
EXECUTE resume_intake_outbox_stmt;
DEALLOCATE PREPARE resume_intake_outbox_stmt;
SET @resume_intake_outbox_sql = NULL;
