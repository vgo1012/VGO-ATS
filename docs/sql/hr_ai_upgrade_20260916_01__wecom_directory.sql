-- 版本：20260916_01；企业微信来源目录与同步批次。
-- 前提：MySQL 8，选择已部署本系统的数据库；备份并停止旧应用写入，保持同步开关关闭。
-- 数据影响：仅新增四张独立目录表，不改现有部门、账号、角色、密码及招聘授权关系。
-- 元数据：限定 DATABASE() 验证现有系统表；新表用 IF NOT EXISTS 检查同库对象，原子建表支持中断续跑。
-- 重跑：不删除或覆盖任何已同步数据；无列级多步变更，新表已完整存在时跳过。
-- 恢复：关闭 hr.wecom.sync.enabled，保留四表与批次用于诊断，不回滚或删除历史身份。
SET NAMES utf8mb4;
SET @wecom_prerequisite = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='identity_user_account')=1,
    'DO 0', 'SELECT `wecom upgrade requires existing HR database`');
PREPARE wecom_prerequisite_stmt FROM @wecom_prerequisite;
EXECUTE wecom_prerequisite_stmt;
DEALLOCATE PREPARE wecom_prerequisite_stmt;

CREATE TABLE IF NOT EXISTS identity_wecom_sync_state (
    corp_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '企业微信企业身份',
    active_batch_id VARCHAR(36) NULL COMMENT '当前排队或执行批次',
    published_batch_id VARCHAR(36) NULL COMMENT '最后发布成功批次',
    next_allowed_at DATETIME(6) NULL COMMENT '数据库时间冷却截止',
    ext TEXT NULL COMMENT 'JSON 扩展信息',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (corp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业微信企业级同步互斥与发布状态';

CREATE TABLE IF NOT EXISTS identity_wecom_sync_batch (
    id VARCHAR(36) NOT NULL COMMENT '同步批次标识',
    corp_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '企业微信企业身份',
    request_key VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '企业内幂等请求键',
    trigger_type VARCHAR(16) NOT NULL COMMENT '手动或每日触发来源',
    actor_user_id VARCHAR(64) NULL COMMENT '实际发起管理员标识，定时为空',
    retry_of_batch_id VARCHAR(36) NULL COMMENT '重试来源失败批次',
    scope_version VARCHAR(64) NOT NULL COMMENT '运行配置范围版本',
    status VARCHAR(24) NOT NULL COMMENT '排队执行成功警告失败或中断状态',
    requested_at DATETIME(6) NOT NULL COMMENT '请求受理时间',
    started_at DATETIME(6) NULL COMMENT '实际开始时间',
    finished_at DATETIME(6) NULL COMMENT '实际结束时间',
    lease_until DATETIME(6) NOT NULL COMMENT '数据库时间租约截止',
    run_token VARCHAR(36) NULL COMMENT '执行围栏令牌',
    error_code VARCHAR(64) NULL COMMENT '脱敏错误编码',
    safe_message VARCHAR(512) NULL COMMENT '可展示结果摘要',
    department_count INT NOT NULL DEFAULT 0 COMMENT '校验通过部门数',
    member_count INT NOT NULL DEFAULT 0 COMMENT '校验通过去重成员数',
    inserted_count INT NOT NULL DEFAULT 0 COMMENT '新增目录记录数',
    changed_count INT NOT NULL DEFAULT 0 COMMENT '变更目录记录数',
    missing_count INT NOT NULL DEFAULT 0 COMMENT '本批未出现待核实人数',
    ext TEXT NULL COMMENT 'JSON 扩展信息',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wecom_batch_request (corp_id, request_key),
    KEY idx_wecom_batch_history (corp_id, requested_at, id),
    CONSTRAINT ck_wecom_batch_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','WARNING','FAILED','INTERRUPTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业微信同步批次与差异摘要';

CREATE TABLE IF NOT EXISTS identity_wecom_department (
    id VARCHAR(36) NOT NULL COMMENT '稳定本地目录部门标识',
    corp_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '企业微信企业身份',
    department_id BIGINT NOT NULL COMMENT '企业内来源部门身份',
    parent_id BIGINT NOT NULL COMMENT '来源父部门身份，允许授权范围外',
    name VARCHAR(255) NOT NULL COMMENT '来源部门名称',
    source_order BIGINT NOT NULL COMMENT '来源部门排序',
    parent_outside_scope BOOLEAN NOT NULL COMMENT '上级是否在授权范围外',
    changed_batch_id VARCHAR(36) NOT NULL COMMENT '最后变更批次标识',
    ext TEXT NULL COMMENT 'JSON 扩展信息',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wecom_department_identity (corp_id, department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业微信来源部门目录，不直接授予本地部门权限';

CREATE TABLE IF NOT EXISTS identity_wecom_member (
    id VARCHAR(36) NOT NULL COMMENT '稳定本地目录人员标识，不是登录账号',
    corp_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '企业微信企业身份',
    user_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '企业内来源人员身份',
    name VARCHAR(255) NOT NULL COMMENT '来源姓名',
    position VARCHAR(255) NULL COMMENT '来源职位',
    business_email VARCHAR(320) NULL COMMENT '来源企业邮箱，未经登录绑定核实',
    source_status INT NULL COMMENT '来源成员状态，不推导登录资格',
    department_ids_json JSON NOT NULL COMMENT '来源多部门关系，保留范围外部门身份',
    direct_leader_ids_json JSON NULL COMMENT '来源直属领导身份列表，空值表示未知而非无领导',
    directory_status VARCHAR(24) NOT NULL COMMENT '可见或待核实状态',
    changed_batch_id VARCHAR(36) NOT NULL COMMENT '最后变更批次标识',
    ext TEXT NULL COMMENT 'JSON 扩展信息',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wecom_member_identity (corp_id, user_id),
    CONSTRAINT ck_wecom_member_visibility CHECK (directory_status IN ('VISIBLE','PENDING_VERIFICATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业微信来源人员及关系，不开通登录或覆盖授权树';
