-- 前置：已部署 20260907_05 来源 JD 草稿及完整人才表。
-- 数据影响：只新增两张来源追溯表，不修改或删除历史数据。
-- CREATE TABLE IF NOT EXISTS 检查当前库元数据，支持第一张表完成后的续跑和重复执行。

CREATE TABLE IF NOT EXISTS `demand_mail_source_message`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    jd_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '职位描述版本 ID',
    source_message_id VARCHAR(998) NOT NULL COMMENT '原始邮件 Message-ID',
    message_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邮件标识哈希',
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邮件正文和来源指纹',
    sender_email VARCHAR(254) NOT NULL COMMENT '发件邮箱',
    recipient_email VARCHAR(254) NOT NULL COMMENT '收件邮箱',
    subject VARCHAR(500) NOT NULL COMMENT '邮件主题',
    plain_body MEDIUMTEXT NOT NULL COMMENT '原始邮件正文',
    delivery_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '传输方式',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_mail_source_actor_message UNIQUE (create_id,message_hash),
    CONSTRAINT ck_mail_source_delivery CHECK (delivery_type = 'MAIL_SIMULATION'),
    CONSTRAINT fk_mail_source_jd FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version(id) ON DELETE RESTRICT,
    INDEX ix_mail_source_jd (jd_version_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟邮件来源';

CREATE TABLE IF NOT EXISTS `demand_jd_resume_source`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    mail_source_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邮件来源记录 ID',
    candidate_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    candidate_name VARCHAR(120) NOT NULL COMMENT '核对时的候选人名称',
    resume_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '简历版本 ID',
    original_file_name VARCHAR(500) NOT NULL COMMENT '原始文件名称',
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '附件内容哈希',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_jd_resume_source_attachment UNIQUE (mail_source_id,content_sha256),
    CONSTRAINT fk_jd_resume_source_mail FOREIGN KEY (mail_source_id) REFERENCES demand_mail_source_message(id) ON DELETE RESTRICT,
    CONSTRAINT fk_jd_resume_source_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate(id) ON DELETE RESTRICT,
    CONSTRAINT fk_jd_resume_source_version FOREIGN KEY (resume_version_id) REFERENCES talent_resume_version(id) ON DELETE RESTRICT,
    INDEX ix_jd_resume_source_time (mail_source_id,create_time DESC,id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件与简历版本关联';
