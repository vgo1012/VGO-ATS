-- 版本：20260917_04；新增面试企业微信日程/会议持久化投影与调用审计。
-- 前提：MySQL 8，已完成当前基线或此前全部升级，并已选择目标数据库。
-- 数据影响：只新增两张空表，不读取、修改或补建历史面试的外部对象。
-- 重跑与部分完成：表使用 IF NOT EXISTS；创建后再次核对关键列、唯一索引和外键，兼容完整重跑。
-- 恢复：应用回退可保留空闲投影表；确需删除时先关闭 worker、取消全部 active_type 非空对象并备份。
SET NAMES utf8mb4;

SET @wecom_calendar_sql = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_interview' AND ENGINE='InnoDB')=1,
    'DO 0', 'SELECT `wecom interview calendar upgrade requires interview_interview InnoDB table`');
PREPARE wecom_calendar_stmt FROM @wecom_calendar_sql;
EXECUTE wecom_calendar_stmt;
DEALLOCATE PREPARE wecom_calendar_stmt;

CREATE TABLE IF NOT EXISTS interview_external_calendar_projection (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '投影主键 ID',
    interview_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '面试 ID',
    provider VARCHAR(20) NOT NULL COMMENT '外部提供方',
    desired_type VARCHAR(24) NOT NULL COMMENT '本地期望的外部对象类型',
    active_type VARCHAR(24) NULL COMMENT '当前已创建的外部对象类型',
    status VARCHAR(20) NOT NULL COMMENT '投影同步状态',
    operation_revision BIGINT NOT NULL COMMENT '期望状态修订号',
    title VARCHAR(120) NULL COMMENT '外部日程或会议标题',
    description VARCHAR(500) NULL COMMENT '外部日程或会议说明',
    admin_user_id VARCHAR(128) COLLATE utf8mb4_0900_bin NULL COMMENT '企业微信管理员 userid',
    invitee_user_ids_json JSON NOT NULL COMMENT '合并后的企业微信受邀 userid 列表',
    additional_invitee_user_ids_json JSON NOT NULL COMMENT '表单额外选择的企业微信受邀 userid 列表',
    start_at DATETIME(6) NOT NULL COMMENT '外部对象开始时间',
    end_at DATETIME(6) NOT NULL COMMENT '外部对象结束时间',
    timezone_id VARCHAR(64) NOT NULL COMMENT 'IANA 时区标识',
    location_text VARCHAR(500) NULL COMMENT '地点文本',
    reminder_seconds INT NOT NULL DEFAULT 300 COMMENT '提前提醒秒数',
    waiting_room_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '会议是否开启等候室',
    allow_enter_before_host BOOLEAN NOT NULL DEFAULT TRUE COMMENT '会议是否允许主持人前入会',
    enter_mute_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '会议成员入会是否静音',
    external_object_id VARCHAR(200) COLLATE utf8mb4_0900_bin NULL COMMENT '企业微信会议 ID 或日程 ID',
    meeting_code VARCHAR(64) NULL COMMENT '企业微信会议号',
    meeting_link VARCHAR(1000) NULL COMMENT '企业微信会议链接',
    remote_status VARCHAR(64) NULL COMMENT '企业微信远端状态快照',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '累计远端尝试次数',
    next_attempt_at DATETIME(6) NOT NULL COMMENT '下次允许处理时间',
    lease_token CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '处理租约令牌',
    lease_until DATETIME(6) NULL COMMENT '处理租约截止时间',
    last_error_code VARCHAR(80) NULL COMMENT '最近一次安全错误编码',
    last_error_detail VARCHAR(500) NULL COMMENT '最近一次安全错误详情',
    last_synced_at DATETIME(6) NULL COMMENT '最近一次同步成功时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    ext TEXT NULL COMMENT 'JSON 扩展信息', remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID', create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID', update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_interview_external_calendar_interview (interview_id),
    KEY ix_interview_external_calendar_due (status,next_attempt_at,lease_until,id),
    CONSTRAINT ck_interview_external_calendar_provider CHECK (provider='WECOM'),
    CONSTRAINT ck_interview_external_calendar_desired CHECK (desired_type IN ('LOCAL_ONLY','WECOM_SCHEDULE','WECOM_MEETING')),
    CONSTRAINT ck_interview_external_calendar_active CHECK (active_type IS NULL OR active_type IN ('WECOM_SCHEDULE','WECOM_MEETING')),
    CONSTRAINT ck_interview_external_calendar_status CHECK (status IN ('PENDING','PROCESSING','SYNCED','CANCELLED','FAILED')),
    CONSTRAINT ck_interview_external_calendar_time CHECK (end_at>start_at),
    CONSTRAINT ck_interview_external_calendar_attempt CHECK (attempt_count>=0),
    CONSTRAINT fk_interview_external_calendar_interview FOREIGN KEY (interview_id) REFERENCES interview_interview(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试企业微信日程或会议持久化投影';

CREATE TABLE IF NOT EXISTS interview_external_calendar_attempt (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '尝试主键 ID',
    projection_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '外部投影 ID',
    operation_revision BIGINT NOT NULL COMMENT '执行时的期望状态修订号',
    operation_type VARCHAR(16) NOT NULL COMMENT 'CREATE、UPDATE 或 CANCEL',
    external_type VARCHAR(24) NOT NULL COMMENT '本次操作的外部对象类型',
    status VARCHAR(16) NOT NULL COMMENT '执行结果',
    external_object_id VARCHAR(200) COLLATE utf8mb4_0900_bin NULL COMMENT '远端对象 ID',
    error_code VARCHAR(80) NULL COMMENT '安全错误编码', error_detail VARCHAR(500) NULL COMMENT '安全错误详情',
    occurred_at DATETIME(6) NOT NULL COMMENT '尝试发生时间',
    ext TEXT NULL COMMENT 'JSON 扩展信息', remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID', create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID', update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    KEY ix_interview_external_calendar_attempt (projection_id,occurred_at,id),
    CONSTRAINT ck_interview_external_attempt_operation CHECK (operation_type IN ('CREATE','UPDATE','CANCEL')),
    CONSTRAINT ck_interview_external_attempt_type CHECK (external_type IN ('WECOM_SCHEDULE','WECOM_MEETING')),
    CONSTRAINT ck_interview_external_attempt_status CHECK (status IN ('SUCCEEDED','FAILED')),
    CONSTRAINT fk_interview_external_attempt_projection FOREIGN KEY (projection_id) REFERENCES interview_external_calendar_projection(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试企业微信日程或会议调用审计';

SET @wecom_calendar_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_projection'
     AND COLUMN_NAME IN ('interview_id','desired_type','active_type','status','operation_revision','external_object_id','next_attempt_at','version'))=8 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_projection'
     AND INDEX_NAME='uk_interview_external_calendar_interview' AND NON_UNIQUE=0)=1 AND
    (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_projection'
     AND CONSTRAINT_NAME='fk_interview_external_calendar_interview')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_attempt'
     AND COLUMN_NAME IN ('projection_id','operation_revision','operation_type','external_type','status','occurred_at'))=6,
    'DO 0', 'SELECT `existing wecom interview calendar tables are incomplete or incompatible`');
PREPARE wecom_calendar_stmt FROM @wecom_calendar_sql;
EXECUTE wecom_calendar_stmt;
DEALLOCATE PREPARE wecom_calendar_stmt;
SET @wecom_calendar_sql = NULL;
