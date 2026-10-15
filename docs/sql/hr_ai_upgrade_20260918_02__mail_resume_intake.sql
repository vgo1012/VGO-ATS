-- 版本：20260918_02；新增单一公共邮箱简历采集、人工 JD 校验和运行指标表。
-- 前提：MySQL 8，已存在 demand_jd、demand_jd_version、talent_source_channel 和 talent_resume_intake。
-- 数据影响：仅新增表，不修改或删除现有业务数据；首次启用由应用以邮箱当前最高 UID 建立零基线。
-- 重跑与部分完成：前置表通过元数据校验，四张新表均使用 IF NOT EXISTS，可安全重跑。
-- 恢复：应用回退可保留这些表；确需移除时先停止邮件采集并导出人工关联审计记录。
SET NAMES utf8mb4;

SET @mail_resume_sql = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('demand_jd','demand_jd_version','talent_source_channel','talent_resume_intake')
     AND ENGINE='InnoDB')=4,
    'DO 0', 'SELECT `mail resume upgrade requires current JD, source channel and resume intake tables`');
PREPARE mail_resume_stmt FROM @mail_resume_sql;
EXECUTE mail_resume_stmt;
DEALLOCATE PREPARE mail_resume_stmt;

CREATE TABLE IF NOT EXISTS `talent_mailbox_cursor`
(
    mailbox_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '邮箱配置稳定键',
    folder VARCHAR(255) NOT NULL COMMENT 'IMAP 文件夹',
    uid_validity BIGINT UNSIGNED NOT NULL COMMENT 'IMAP UIDVALIDITY',
    last_uid BIGINT UNSIGNED NOT NULL COMMENT '最后已持久化处理的 UID',
    initialized_at TIMESTAMP(6) NOT NULL COMMENT '零基线建立时间',
    last_success_at TIMESTAMP(6) NOT NULL COMMENT '最近成功推进时间',
    ext TEXT NULL COMMENT 'JSON 扩展信息', remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件简历 IMAP UID 游标';

CREATE TABLE IF NOT EXISTS `talent_mail_ingest_run`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '运行记录 ID',
    slot_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '五分钟运行幂等键',
    mailbox_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邮箱配置稳定键',
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运行状态',
    scanned_count INT NOT NULL DEFAULT 0 COMMENT '扫描邮件数',
    attachment_count INT NOT NULL DEFAULT 0 COMMENT '新增附件数',
    auto_matched_count INT NOT NULL DEFAULT 0 COMMENT '自动匹配数',
    review_required_count INT NOT NULL DEFAULT 0 COMMENT '待人工匹配数',
    duplicate_count INT NOT NULL DEFAULT 0 COMMENT '重复数',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
    recent_error_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '最近错误编码',
    recent_error_detail VARCHAR(500) NULL COMMENT '最近安全错误详情',
    started_at TIMESTAMP(6) NULL COMMENT '开始时间', finished_at TIMESTAMP(6) NULL COMMENT '结束时间',
    ext TEXT NULL COMMENT 'JSON 扩展信息', remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_mail_ingest_run_slot UNIQUE (slot_key),
    INDEX ix_mail_ingest_run_time (create_time DESC, id DESC),
    CONSTRAINT ck_mail_ingest_run_status CHECK (status IN ('QUEUED','RUNNING','PARTIAL','SUCCEEDED','FAILED')),
    CONSTRAINT ck_mail_ingest_run_counts CHECK (scanned_count >= 0 AND attachment_count >= 0 AND auto_matched_count >= 0 AND review_required_count >= 0 AND duplicate_count >= 0 AND failed_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件简历采集运行记录';

CREATE TABLE IF NOT EXISTS `talent_mail_route_rule`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '规则 ID',
    mailbox_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邮箱配置稳定键',
    normalized_title VARCHAR(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'NFKC 后岗位文本',
    normalized_region VARCHAR(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'NFKC 后区域',
    jd_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '逻辑 JD ID',
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规则状态',
    active_marker CHAR(1) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前有效唯一标记',
    ext TEXT NULL COMMENT 'JSON 扩展信息', remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_mail_route_rule_active UNIQUE (mailbox_key, normalized_title, normalized_region, active_marker),
    CONSTRAINT fk_mail_route_rule_jd FOREIGN KEY (jd_id) REFERENCES demand_jd(id) ON DELETE RESTRICT,
    CONSTRAINT ck_mail_route_rule_status CHECK (status IN ('ACTIVE','DISABLED')),
    CONSTRAINT ck_mail_route_rule_active CHECK ((status='ACTIVE' AND active_marker='Y') OR (status='DISABLED' AND active_marker IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件岗位区域精确路由规则';

CREATE TABLE IF NOT EXISTS `talent_mail_resume`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '邮件简历记录 ID',
    mailbox_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邮箱配置稳定键',
    folder VARCHAR(255) NOT NULL COMMENT 'IMAP 文件夹',
    source_channel_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冻结的来源渠道 ID',
    uid_validity BIGINT UNSIGNED NOT NULL COMMENT 'IMAP UIDVALIDITY', mail_uid BIGINT UNSIGNED NOT NULL COMMENT 'IMAP UID',
    message_id_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Message-ID SHA-256',
    sender_email VARCHAR(320) NULL COMMENT '发件人邮箱', subject VARCHAR(500) NULL COMMENT '邮件主题',
    received_at TIMESTAMP(6) NOT NULL COMMENT '邮件收件时间', attachment_name VARCHAR(255) NULL COMMENT '简历附件名',
    media_type VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '附件媒体类型', size_bytes BIGINT NULL COMMENT '附件大小',
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '附件 SHA-256',
    object_key VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '对象存储键',
    parsed_jd_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '识别出的稳定 JD 编号',
    parsed_jd_title VARCHAR(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '识别出的规范化岗位文本',
    parsed_region VARCHAR(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '识别出的规范化区域',
    match_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '匹配结果类型',
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '处理状态',
    failure_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '失败或待校验原因编码',
    failure_detail VARCHAR(500) NULL COMMENT '安全失败详情',
    jd_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关联逻辑 JD ID',
    jd_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关联时当前 JD 版本 ID',
    owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关联时当前负责人',
    owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关联时当前归属部门',
    intake_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '下发后的标准收件 ID',
    linked_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '人工关联操作人',
    linked_at TIMESTAMP(6) NULL COMMENT '人工关联时间', link_reason VARCHAR(500) NULL COMMENT '人工关联原因',
    ext TEXT NULL COMMENT 'JSON 扩展信息', remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID', create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID', update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_mail_resume_uid UNIQUE (mailbox_key, folder, uid_validity, mail_uid),
    INDEX ix_mail_resume_review (status, received_at DESC, id DESC), INDEX ix_mail_resume_message_id (message_id_hash),
    INDEX ix_mail_resume_content (content_sha256), INDEX ix_mail_resume_jd (jd_id, received_at DESC, id DESC),
    CONSTRAINT fk_mail_resume_channel FOREIGN KEY (source_channel_id) REFERENCES talent_source_channel(id) ON DELETE RESTRICT,
    CONSTRAINT fk_mail_resume_jd FOREIGN KEY (jd_id) REFERENCES demand_jd(id) ON DELETE RESTRICT,
    CONSTRAINT fk_mail_resume_jd_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_mail_resume_intake FOREIGN KEY (intake_id) REFERENCES talent_resume_intake(id) ON DELETE RESTRICT,
    CONSTRAINT ck_mail_resume_status CHECK (status IN ('REVIEW_REQUIRED','MATCHED','DISPATCHED','DISPATCH_FAILED','REJECTED','FAILED')),
    CONSTRAINT ck_mail_resume_match CHECK (match_type IS NULL OR match_type IN ('JD_CODE','CONFIRMED_RULE','EXACT_TITLE_REGION','REVIEW_REQUIRED')),
    CONSTRAINT ck_mail_resume_size CHECK (size_bytes IS NULL OR size_bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公共邮箱简历采集与 JD 校验记录';

SET @mail_resume_sql = NULL;
