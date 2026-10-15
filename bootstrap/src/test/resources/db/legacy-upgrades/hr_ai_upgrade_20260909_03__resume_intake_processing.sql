-- 前置：已部署人才收件、简历版本和异步任务基础表，并选中待升级业务库；执行前备份。
-- 数据影响：已有收件保持原状态；新上传文件按 SHA-256 共享一次后台解析，来源批次和岗位关联仍逐条保留。
-- 失败处理记录可原位重试；回退前须停用新版收件 Worker，确认无 RUNNING 记录并备份处理结果。
SET NAMES utf8mb4;

SET @hr_resume_intake_sql = IF(DATABASE() IS NOT NULL AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('talent_resume_intake','talent_intake_batch'))=2, 'DO 0', 'SELECT `resume intake processing upgrade requires selected database and prerequisite tables`');
PREPARE hr_resume_intake_stmt FROM @hr_resume_intake_sql;
EXECUTE hr_resume_intake_stmt;
DEALLOCATE PREPARE hr_resume_intake_stmt;

CREATE TABLE IF NOT EXISTS `talent_resume_intake_processing`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '共享处理 ID',
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原文件 SHA-256；全局去重键',
    original_file_name VARCHAR(255) NOT NULL COMMENT '首次上传的安全文件名',
    media_type VARCHAR(100) NOT NULL COMMENT '文件媒体类型',
    object_key VARCHAR(500) NOT NULL COMMENT '扫描通过后的对象存储键',
    status VARCHAR(32) NOT NULL COMMENT 'QUEUED/RUNNING/SUCCEEDED/FAILED_RETRYABLE/FAILED_FINAL',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '已领取处理次数',
    max_attempts INT NOT NULL DEFAULT 3 COMMENT '最大处理次数',
    next_retry_at TIMESTAMP(6) NULL COMMENT '最早重试时间',
    lease_owner VARCHAR(100) NULL COMMENT 'Worker 租约持有者',
    lease_expires_at TIMESTAMP(6) NULL COMMENT 'Worker 租约到期时间',
    extracted_pages_json LONGTEXT NULL COMMENT '已校验的逐页抽取文本 JSON',
    parsed_facts_json LONGTEXT NULL COMMENT '已校验的简历事实 JSON',
    model_provider VARCHAR(100) NULL COMMENT '模型供应方',
    model_version VARCHAR(100) NULL COMMENT '模型版本',
    raw_output_object_key VARCHAR(500) NULL COMMENT '加密归档的模型原始输出对象键',
    raw_output_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型原始输出 SHA-256',
    raw_output_size BIGINT NULL COMMENT '模型原始输出字节数',
    encryption_version VARCHAR(100) NULL COMMENT '模型输出归档加密版本',
    failure_code VARCHAR(100) NULL COMMENT '稳定失败编码',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    ext TEXT NULL COMMENT 'JSON 扩展信息',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_talent_intake_processing_hash (content_sha256),
    INDEX ix_talent_intake_processing_claim (status, next_retry_at, lease_expires_at, create_time, id),
    CONSTRAINT ck_talent_intake_processing_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED_RETRYABLE','FAILED_FINAL')),
    CONSTRAINT ck_talent_intake_processing_attempt CHECK (attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts),
    CONSTRAINT ck_talent_intake_processing_result CHECK ((status='SUCCEEDED' AND parsed_facts_json IS NOT NULL AND failure_code IS NULL) OR status!='SUCCEEDED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按文件内容共享的简历收件解析处理';

SET @hr_resume_intake_sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake' AND COLUMN_NAME='processing_id'), 'DO 0', 'ALTER TABLE talent_resume_intake ADD COLUMN processing_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''共享解析处理 ID'' AFTER object_key');
PREPARE hr_resume_intake_stmt FROM @hr_resume_intake_sql;
EXECUTE hr_resume_intake_stmt;
DEALLOCATE PREPARE hr_resume_intake_stmt;

-- 原约束存在时原子替换，避免升级中断留下无状态约束的窗口；新版约束重跑保持不变。
SET @hr_resume_intake_check_exists = EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake' AND CONSTRAINT_NAME='ck_talent_intake_status');
SET @hr_resume_intake_check_current = EXISTS (SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_talent_intake_status' AND CHECK_CLAUSE LIKE '%IDENTITY_REVIEW_REQUIRED%');
SET @hr_resume_intake_sql = IF(@hr_resume_intake_check_current, 'DO 0', CONCAT('ALTER TABLE talent_resume_intake ', IF(@hr_resume_intake_check_exists, 'DROP CHECK ck_talent_intake_status, ', ''), 'ADD CONSTRAINT ck_talent_intake_status CHECK (status IN (''PARSING'',''IDENTITY_REVIEW_REQUIRED'',''COMPLETED'',''READY_FOR_CONFIRMATION'',''CONFIRMED'',''IGNORED'',''REJECTED'',''QUARANTINED'',''FAILED_RETRYABLE''))'));
PREPARE hr_resume_intake_stmt FROM @hr_resume_intake_sql;
EXECUTE hr_resume_intake_stmt;
DEALLOCATE PREPARE hr_resume_intake_stmt;

SET @hr_resume_intake_sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake' AND COLUMN_NAME='score_run_id'), 'DO 0', 'ALTER TABLE talent_resume_intake ADD COLUMN score_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''关联岗位评分运行 ID'' AFTER resume_version_id');
PREPARE hr_resume_intake_stmt FROM @hr_resume_intake_sql;
EXECUTE hr_resume_intake_stmt;
DEALLOCATE PREPARE hr_resume_intake_stmt;

SET @hr_resume_intake_sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake' AND COLUMN_NAME='score_status'), 'DO 0', 'ALTER TABLE talent_resume_intake ADD COLUMN score_status VARCHAR(32) NOT NULL DEFAULT ''NOT_APPLICABLE'' COMMENT ''岗位评分状态'' AFTER score_run_id');
PREPARE hr_resume_intake_stmt FROM @hr_resume_intake_sql;
EXECUTE hr_resume_intake_stmt;
DEALLOCATE PREPARE hr_resume_intake_stmt;

SET @hr_resume_intake_sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake' AND INDEX_NAME='ix_talent_intake_processing'), 'DO 0', 'ALTER TABLE talent_resume_intake ADD INDEX ix_talent_intake_processing (processing_id, id)');
PREPARE hr_resume_intake_stmt FROM @hr_resume_intake_sql;
EXECUTE hr_resume_intake_stmt;
DEALLOCATE PREPARE hr_resume_intake_stmt;

SET @hr_resume_intake_sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='talent_resume_intake' AND CONSTRAINT_NAME='fk_talent_intake_processing'), 'DO 0', 'ALTER TABLE talent_resume_intake ADD CONSTRAINT fk_talent_intake_processing FOREIGN KEY (processing_id) REFERENCES talent_resume_intake_processing(id) ON DELETE RESTRICT');
PREPARE hr_resume_intake_stmt FROM @hr_resume_intake_sql;
EXECUTE hr_resume_intake_stmt;
DEALLOCATE PREPARE hr_resume_intake_stmt;
