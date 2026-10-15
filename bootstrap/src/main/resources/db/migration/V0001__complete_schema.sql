-- VGO ATS 独立公开发行的 MySQL 8 初始数据库基线。
-- 仅用于全新的 VGO 空库；不得用于已有公司数据库的原地升级。
-- 本文件是仓库内维护的权威单文件数据库基线。
-- 执行前请先选择目标空数据库。
-- 本文件仅包含最终表结构和必需的初始化数据。
SET NAMES utf8mb4;
SET
@HR_AI_OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS;
SET
FOREIGN_KEY_CHECKS = 0;

-- Final table definitions
CREATE TABLE `platform_idempotency_record`
(
    namespace     VARCHAR(80)  NOT NULL COMMENT '幂等记录命名空间',
    key_hash      CHAR(64)     NOT NULL COMMENT '幂等键哈希值',
    request_hash  CHAR(64)     NOT NULL COMMENT '请求内容哈希值',
    status        VARCHAR(24)  NOT NULL COMMENT '当前状态',
    http_status   SMALLINT UNSIGNED NULL COMMENT 'HTTP 响应状态码',
    response_json JSON NULL COMMENT '缓存的响应内容（JSON）',
    locked_until  TIMESTAMP(6) NULL COMMENT '处理锁定截止时间',
    completed_at  TIMESTAMP(6) NULL COMMENT '完成时间',
    expires_at    TIMESTAMP(6) NOT NULL COMMENT '过期时间',
    row_version   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '行级乐观锁版本号',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (namespace, key_hash),
    INDEX         idx_platform_idempotency_expiry (expires_at),
    CONSTRAINT chk_platform_idempotency_status
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台级 HTTP 幂等请求记录';

CREATE TABLE `identity_department`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    code                 VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务编码',
    name                 VARCHAR(100)                                      NOT NULL COMMENT '名称',
    parent_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '上级部门 ID',
    status               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    `version`            INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_identity_department_code UNIQUE (code),
    CONSTRAINT fk_identity_department_parent FOREIGN KEY (parent_department_id)
        REFERENCES identity_department (id) ON DELETE RESTRICT,
    CONSTRAINT ck_identity_department_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='组织部门';

CREATE TABLE `identity_user_account`
(
    id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    display_name          VARCHAR(100)                                       NOT NULL COMMENT '用户展示名称',
    work_email            VARCHAR(320)                                       NOT NULL COMMENT '工作邮箱',
    normalized_email      VARCHAR(320) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用于唯一匹配的规范化邮箱',
    credential_hash       VARCHAR(255)                                       NOT NULL COMMENT '登录凭据哈希值',
    source_title          VARCHAR(100) NULL COMMENT '来源职位名称',
    home_department_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '用户主部门 ID',
    direct_leader_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '直属领导用户 ID',
    status                VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    must_change_password  BOOLEAN                                            NOT NULL COMMENT '下次登录是否必须修改密码',
    registered_at         TIMESTAMP(6)                                       NOT NULL COMMENT '注册时间',
    credential_reset_at   TIMESTAMP(6) NULL COMMENT '凭据最近重置时间',
    disabled_at           TIMESTAMP(6) NULL COMMENT '停用时间',
    `version`             INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_identity_user_normalized_email UNIQUE (normalized_email),
    CONSTRAINT fk_identity_user_home_department FOREIGN KEY (home_department_id)
        REFERENCES identity_department (id) ON DELETE RESTRICT,
    CONSTRAINT fk_identity_user_direct_leader FOREIGN KEY (direct_leader_user_id)
        REFERENCES identity_user_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_identity_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED')),
    INDEX                 ix_identity_user_leader (direct_leader_user_id),
    INDEX                 ix_identity_user_department_status (home_department_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账户';

CREATE TABLE `identity_user_account_history`
(
    id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    user_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '用户 ID',
    change_type     VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更类型',
    before_snapshot JSON NULL COMMENT '变更前数据快照（JSON）',
    after_snapshot  JSON                                              NOT NULL COMMENT '变更后数据快照（JSON）',
    reason          VARCHAR(500) NULL COMMENT '操作原因',
    actor_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际操作人用户 ID',
    occurred_at     TIMESTAMP(6)                                      NOT NULL COMMENT '事件发生时间',
    account_version BIGINT                                            NOT NULL COMMENT '账户版本',
    `ext`           TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`        VARCHAR(255) NULL COMMENT '备注',
    `create_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`   VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`   VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`       INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_identity_user_history_user FOREIGN KEY (user_id)
        REFERENCES identity_user_account (id) ON DELETE RESTRICT,
    INDEX           ix_identity_user_history_user_time (user_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账户变更历史';

CREATE TABLE `identity_user_support_department`
(
    user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID',
    department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '部门 ID',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (user_id, department_id),
    CONSTRAINT fk_identity_support_user FOREIGN KEY (user_id)
        REFERENCES identity_user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_identity_support_department FOREIGN KEY (department_id)
        REFERENCES identity_department (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户支援部门关系';

CREATE TABLE `identity_membership`
(
    user_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '用户 ID',
    department_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '部门 ID',
    membership_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成员关系类型',
    `ext`           TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`        VARCHAR(255) NULL COMMENT '备注',
    `create_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`   VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`   VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`       INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (user_id, department_id, membership_type),
    CONSTRAINT fk_identity_membership_user FOREIGN KEY (user_id)
        REFERENCES identity_user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_identity_membership_department FOREIGN KEY (department_id)
        REFERENCES identity_department (id) ON DELETE RESTRICT,
    CONSTRAINT ck_identity_membership_type CHECK (membership_type IN ('HOME', 'SUPPORT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户部门成员关系';

CREATE TABLE `identity_role`
(
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    code          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务编码',
    name          VARCHAR(100)                                      NOT NULL COMMENT '名称',
    system_role   BOOLEAN                                           NOT NULL COMMENT '是否为系统内置角色',
    status        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    `version`     INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_identity_role_code UNIQUE (code),
    CONSTRAINT ck_identity_role_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色';

CREATE TABLE `identity_permission`
(
    code          VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '业务编码',
    name          VARCHAR(120)                                      NOT NULL COMMENT '名称',
    category      VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分类',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统权限';

CREATE TABLE `identity_role_permission`
(
    role_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '角色 ID',
    permission_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限编码',
    `ext`           TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`        VARCHAR(255) NULL COMMENT '备注',
    `create_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`   VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`   TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`   VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`   TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`       INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_identity_role_permission_role FOREIGN KEY (role_id)
        REFERENCES identity_role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_identity_role_permission_permission FOREIGN KEY (permission_code)
        REFERENCES identity_permission (code) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关系';

CREATE TABLE `identity_user_role`
(
    user_id             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID',
    role_id             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色 ID',
    assigned_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分配操作人用户 ID',
    assigned_at         TIMESTAMP(6)                                   NOT NULL COMMENT '分配时间',
    reason              VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_identity_user_role_user FOREIGN KEY (user_id)
        REFERENCES identity_user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_identity_user_role_role FOREIGN KEY (role_id)
        REFERENCES identity_role (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关系';

CREATE TABLE `identity_scope_grant`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    user_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '用户 ID',
    scope_type         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '权限范围类型',
    scope_value        VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限范围值',
    granted_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '授权操作人用户 ID',
    granted_at         TIMESTAMP(6)                                       NOT NULL COMMENT '授权时间',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_identity_scope_grant UNIQUE (user_id, scope_type, scope_value),
    CONSTRAINT fk_identity_scope_grant_user FOREIGN KEY (user_id)
        REFERENCES identity_user_account (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户数据范围授权';

CREATE TABLE `identity_object_grant`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    user_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '用户 ID',
    object_type        VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '授权对象类型',
    object_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '授权对象 ID',
    permission_code    VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限编码',
    granted_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '授权操作人用户 ID',
    granted_at         TIMESTAMP(6)                                       NOT NULL COMMENT '授权时间',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_identity_object_grant UNIQUE (user_id, object_type, object_id, permission_code),
    CONSTRAINT fk_identity_object_grant_user FOREIGN KEY (user_id)
        REFERENCES identity_user_account (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户对象级授权';

CREATE TABLE `identity_handover_batch`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    source_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '来源用户 ID',
    target_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '目标用户 ID',
    selection_mode       VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '选择模式',
    status               VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    total_count          INT                                                NOT NULL DEFAULT 0 COMMENT '总记录数',
    succeeded_count      INT                                                NOT NULL DEFAULT 0 COMMENT '成功记录数',
    failed_count         INT                                                NOT NULL DEFAULT 0 COMMENT '失败记录数',
    unresolved_count     INT                                                NOT NULL DEFAULT 0 COMMENT '未解决记录数',
    reason               VARCHAR(500)                                       NOT NULL COMMENT '操作原因',
    requested_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求发起人用户 ID',
    started_at           TIMESTAMP(6) NULL COMMENT '开始处理时间',
    completed_at         TIMESTAMP(6) NULL COMMENT '完成时间',
    `version`            INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    idempotency_key      VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等键',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_identity_handover_idempotency UNIQUE (requested_by_user_id, idempotency_key),
    CONSTRAINT ck_identity_handover_selection CHECK (selection_mode IN ('ALL', 'SELECTED')),
    CONSTRAINT ck_identity_handover_status CHECK (status IN ('PREVIEWED', 'RUNNING', 'SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED')),
    INDEX                ix_identity_handover_source (source_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户工作交接批次';

CREATE TABLE `identity_handover_item`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    batch_id             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '批次 ID',
    resource_type        VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源类型',
    resource_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '资源 ID',
    status               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    mandatory            BOOLEAN                                           NOT NULL COMMENT '是否为强制项',
    error_code           VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    error_message        VARCHAR(500) NULL COMMENT '错误信息',
    from_owner_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '变更前负责人用户 ID',
    to_owner_user_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '变更后负责人用户 ID',
    ownership_history_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '归属变更历史记录 ID',
    processed_at         TIMESTAMP(6) NULL COMMENT '处理时间',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`            INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_identity_handover_item UNIQUE (batch_id, resource_type, resource_id),
    CONSTRAINT fk_identity_handover_item_batch FOREIGN KEY (batch_id)
        REFERENCES identity_handover_batch (id) ON DELETE RESTRICT,
    CONSTRAINT ck_identity_handover_item_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'UNRESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户工作交接条目';

CREATE TABLE `identity_audit_event`
(
    id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    event_type      VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型',
    actor_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '实际操作人用户 ID',
    target_type     VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '目标类型',
    target_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '目标记录 ID',
    reason          VARCHAR(500) NULL COMMENT '操作原因',
    before_snapshot JSON NULL COMMENT '变更前数据快照（JSON）',
    after_snapshot  JSON NULL COMMENT '变更后数据快照（JSON）',
    trace_id        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '链路追踪 ID',
    occurred_at     TIMESTAMP(6)                                       NOT NULL COMMENT '事件发生时间',
    `ext`           TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`        VARCHAR(255) NULL COMMENT '备注',
    `create_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`   VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`   TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`   VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`   TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`       INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX           ix_identity_audit_target (target_type, target_id, occurred_at DESC),
    INDEX           ix_identity_audit_actor (actor_user_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='身份与权限审计事件';

CREATE TABLE `SPRING_SESSION`
(
    PRIMARY_ID            CHAR(36)     NOT NULL COMMENT '主要ID',
    SESSION_ID            CHAR(36)     NOT NULL COMMENT '会话ID',
    CREATION_TIME         BIGINT       NOT NULL COMMENT '创建时间',
    LAST_ACCESS_TIME      BIGINT       NOT NULL COMMENT '最近访问时间',
    MAX_INACTIVE_INTERVAL INT          NOT NULL COMMENT '最大非活动间隔',
    EXPIRY_TIME           BIGINT       NOT NULL COMMENT '过期时间',
    PRINCIPAL_NAME        VARCHAR(100) COMMENT '安全主体名称',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT          NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='Spring Session 会话';

CREATE TABLE `SPRING_SESSION_ATTRIBUTES`
(
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL COMMENT '会话主要ID',
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL COMMENT '属性名称',
    ATTRIBUTE_BYTES    BLOB         NOT NULL COMMENT '会话属性序列化数据',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT          NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='Spring Session 会话属性';

CREATE TABLE `identity_department_assignment_queue`
(
    department_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '部门 ID',
    owner_user_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务负责人用户 ID',
    `version`             INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    configured_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '配置操作人用户 ID',
    configured_at         TIMESTAMP(6)                                   NOT NULL COMMENT '配置时间',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT fk_identity_assignment_queue_department FOREIGN KEY (department_id)
        REFERENCES identity_department (id) ON DELETE RESTRICT,
    CONSTRAINT fk_identity_assignment_queue_owner FOREIGN KEY (owner_user_id)
        REFERENCES identity_user_account (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门默认分配队列';

CREATE TABLE `demand_client`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    code                VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务编码',
    name                VARCHAR(160)                                      NOT NULL COMMENT '名称',
    status              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    owner_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务归属部门 ID',
    `version`           INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_demand_client_code UNIQUE (code),
    CONSTRAINT ck_demand_client_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_demand_client_owner FOREIGN KEY (owner_user_id) REFERENCES identity_user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_demand_client_department FOREIGN KEY (owner_department_id) REFERENCES identity_department (id) ON DELETE RESTRICT,
    INDEX               ix_demand_client_scope (owner_department_id, status, update_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘客户';

CREATE TABLE `demand_client_project`
(
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    client_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '客户 ID',
    code          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务编码',
    name          VARCHAR(160)                                      NOT NULL COMMENT '名称',
    status        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    `version`     INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_demand_client_project_code UNIQUE (client_id, code),
    CONSTRAINT ck_demand_client_project_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_demand_project_client FOREIGN KEY (client_id) REFERENCES demand_client (id) ON DELETE RESTRICT,
    INDEX         ix_demand_project_client_status (client_id, status, update_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户项目';

CREATE TABLE `demand_client_position`
(
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    client_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '客户 ID',
    project_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '项目 ID',
    code          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务编码',
    name          VARCHAR(160)                                      NOT NULL COMMENT '名称',
    status        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    `version`     INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_demand_client_position_code UNIQUE (client_id, code),
    CONSTRAINT ck_demand_client_position_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_demand_position_client FOREIGN KEY (client_id) REFERENCES demand_client (id) ON DELETE RESTRICT,
    CONSTRAINT fk_demand_position_project FOREIGN KEY (project_id) REFERENCES demand_client_project (id) ON DELETE RESTRICT,
    INDEX         ix_demand_position_client_status (client_id, status, update_time DESC, id DESC),
    INDEX         ix_demand_position_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户职位';

CREATE TABLE `demand_recruitment_demand`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    code                VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务编码',
    title               VARCHAR(180)                                      NOT NULL COMMENT '标题',
    client_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '客户 ID',
    project_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '项目 ID',
    position_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '职位 ID',
    recruitment_type    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '招聘类型',
    headcount           INT                                               NOT NULL COMMENT '计划招聘人数',
    description         TEXT NULL COMMENT '描述',
    status              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    owner_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务归属部门 ID',
    `version`           INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_demand_recruitment_code UNIQUE (code),
    CONSTRAINT ck_demand_recruitment_type CHECK (recruitment_type IN ('INTERNAL', 'EXTERNAL')),
    CONSTRAINT ck_demand_recruitment_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED')),
    CONSTRAINT ck_demand_recruitment_headcount CHECK (headcount BETWEEN 1 AND 10000),
    CONSTRAINT fk_demand_recruitment_client FOREIGN KEY (client_id) REFERENCES demand_client (id) ON DELETE RESTRICT,
    CONSTRAINT fk_demand_recruitment_project FOREIGN KEY (project_id) REFERENCES demand_client_project (id) ON DELETE RESTRICT,
    CONSTRAINT fk_demand_recruitment_position FOREIGN KEY (position_id) REFERENCES demand_client_position (id) ON DELETE RESTRICT,
    CONSTRAINT fk_demand_recruitment_owner FOREIGN KEY (owner_user_id) REFERENCES identity_user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_demand_recruitment_department FOREIGN KEY (owner_department_id) REFERENCES identity_department (id) ON DELETE RESTRICT,
    INDEX               ix_demand_recruitment_owner_status (owner_user_id, status, update_time DESC, id DESC),
    INDEX               ix_demand_recruitment_department_status (owner_department_id, status, update_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘需求';

CREATE TABLE `demand_recruitment_collaborator`
(
    demand_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '招聘需求 ID',
    user_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID',
    added_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '添加操作人用户 ID',
    added_at         TIMESTAMP(6)                                   NOT NULL COMMENT '添加时间',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`        INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (demand_id, user_id),
    CONSTRAINT fk_demand_collaborator_demand FOREIGN KEY (demand_id) REFERENCES demand_recruitment_demand (id) ON DELETE RESTRICT,
    CONSTRAINT fk_demand_collaborator_user FOREIGN KEY (user_id) REFERENCES identity_user_account (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘需求协作人关系';

CREATE TABLE `demand_jd`
(
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '系统生成的稳定 JD 编号',
    client_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'JD 客户，可空表示内部招聘',
    owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'JD 归属 HR',
    owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'JD 归属部门',
    recruitment_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'JD 招聘类型',
    public_visible BOOLEAN NOT NULL DEFAULT FALSE COMMENT '管理员维护的公共 JD',
    distinction VARCHAR(180) NULL COMMENT '同名岗位区分说明',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除，保留历史和去重记录',
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    demand_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '招聘需求 ID，来源草稿待补充',
    source_owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '来源草稿归属部门',
    source_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '来源标题和正文指纹',
    title              VARCHAR(180)                                      NOT NULL COMMENT '标题',
    status             VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    current_version_no INT                                               NOT NULL DEFAULT 0 COMMENT '当前业务版本号',
    closed_by_user_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关闭操作人用户 ID',
    closed_at          TIMESTAMP(6) NULL COMMENT '关闭时间',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_demand_jd_code UNIQUE (code),
    CONSTRAINT fk_demand_jd_client FOREIGN KEY (client_id) REFERENCES demand_client (id) ON DELETE RESTRICT,
    CONSTRAINT uk_demand_jd_demand UNIQUE (demand_id),
    CONSTRAINT uk_demand_jd_source UNIQUE (create_id, source_fingerprint),
    CONSTRAINT ck_demand_jd_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED')),
    CONSTRAINT fk_demand_jd_demand FOREIGN KEY (demand_id) REFERENCES demand_recruitment_demand (id) ON DELETE RESTRICT,
    INDEX              ix_demand_jd_status_time (status, create_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职位描述（JD）主记录';

CREATE TABLE `demand_jd_version`
(
    client_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '版本冻结客户',
    recruitment_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '版本冻结招聘类型，内部招聘的空客户不可回填',
    content_owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '内容去重归属人',
    content_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '版本内容指纹，独立新增为空',
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    jd_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '职位描述（JD）ID',
    version_no    INT                                               NOT NULL COMMENT '业务版本号',
    title         VARCHAR(180)                                      NOT NULL COMMENT '标题',
    raw_text      MEDIUMTEXT                                        NOT NULL COMMENT '职位描述原始文本',
    status        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    source_type   VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据来源类型',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_demand_jd_version_client FOREIGN KEY (client_id) REFERENCES demand_client (id) ON DELETE RESTRICT,
    CONSTRAINT uk_jd_version_content UNIQUE (content_owner_user_id, content_fingerprint),
    CONSTRAINT uk_demand_jd_version UNIQUE (jd_id, version_no),
    CONSTRAINT ck_demand_jd_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    CONSTRAINT ck_demand_jd_version_source CHECK (source_type IN ('MANUAL', 'AI_PARSED')),
    CONSTRAINT fk_demand_jd_version_jd FOREIGN KEY (jd_id) REFERENCES demand_jd (id) ON DELETE RESTRICT,
    INDEX         ix_demand_jd_version_jd_time (jd_id, create_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职位描述（JD）版本';

CREATE TABLE `demand_workflow_template_version`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    jd_version_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '职位描述（JD）版本 ID',
    version_no           INT                                               NOT NULL COMMENT '业务版本号',
    recruitment_type     VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '招聘类型',
    status               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    active_slot          TINYINT NULL COMMENT '当前生效版本占位标记',
    `version`            INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    activated_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '生效操作人用户 ID',
    activated_at         TIMESTAMP(6) NULL COMMENT '生效时间',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_demand_workflow_version UNIQUE (jd_version_id, version_no),
    CONSTRAINT uk_demand_workflow_active UNIQUE (jd_version_id, active_slot),
    CONSTRAINT ck_demand_workflow_type CHECK (recruitment_type IN ('INTERNAL', 'EXTERNAL')),
    CONSTRAINT ck_demand_workflow_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_demand_workflow_active_slot CHECK ((status = 'ACTIVE' AND active_slot = 1) OR (status <> 'ACTIVE' AND active_slot IS NULL)),
    CONSTRAINT fk_demand_workflow_jd_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT,
    INDEX                ix_demand_workflow_jd_time (jd_version_id, create_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘流程模板版本';

CREATE TABLE `demand_workflow_step_definition`
(
    id                        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    template_version_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '模板版本 ID',
    step_code                 VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '步骤编码',
    sequence_no               INT                                               NOT NULL COMMENT '顺序号',
    candidate_optional        BOOLEAN                                           NOT NULL COMMENT '候选人是否可以跳过该步骤',
    default_follow_up_channel VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '默认跟进UP渠道',
    `ext`                     TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                  VARCHAR(255) NULL COMMENT '备注',
    `create_id`               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`             VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`             TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`             VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`             TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`                 INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_demand_workflow_step_code UNIQUE (template_version_id, step_code),
    CONSTRAINT uk_demand_workflow_step_sequence UNIQUE (template_version_id, sequence_no),
    CONSTRAINT ck_demand_workflow_step_code CHECK (step_code IN ('CANDIDATE_COMMUNICATION', 'PRE_SUBMISSION_INTERNAL_INTERVIEW', 'CLIENT_SUBMISSION',
                                                                 'CLIENT_AI_INTERVIEW', 'INTERVIEW_ROUND_1', 'INTERVIEW_ROUND_2', 'INTERVIEW_ROUND_3',
                                                                 'DECISION', 'OFFER')),
    CONSTRAINT fk_demand_workflow_step_template FOREIGN KEY (template_version_id) REFERENCES demand_workflow_template_version (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘流程步骤定义';

CREATE TABLE `demand_ownership_history`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    aggregate_type     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合根类型',
    aggregate_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '聚合根 ID',
    from_owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '变更前负责人用户 ID',
    to_owner_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '变更后负责人用户 ID',
    from_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '变更前部门 ID',
    to_department_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '变更后部门 ID',
    reason             VARCHAR(500)                                      NOT NULL COMMENT '操作原因',
    actor_user_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际操作人用户 ID',
    occurred_at        TIMESTAMP(6)                                      NOT NULL COMMENT '事件发生时间',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX              ix_demand_ownership_aggregate (aggregate_type, aggregate_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘需求归属变更历史';

CREATE TABLE `demand_audit_event`
(
    id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    event_type      VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型',
    actor_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际操作人用户 ID',
    target_type     VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标类型',
    target_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '目标记录 ID',
    reason          VARCHAR(500) NULL COMMENT '操作原因',
    before_snapshot JSON NULL COMMENT '变更前数据快照（JSON）',
    after_snapshot  JSON NULL COMMENT '变更后数据快照（JSON）',
    occurred_at     TIMESTAMP(6)                                      NOT NULL COMMENT '事件发生时间',
    `ext`           TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`        VARCHAR(255) NULL COMMENT '备注',
    `create_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`   VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`   VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`       INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX           ix_demand_audit_target (target_type, target_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘需求审计事件';

CREATE TABLE `demand_idempotency_record`
(
    actor_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '实际操作人用户 ID',
    idempotency_key     VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等键',
    action_code         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '操作编码',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求指纹',
    target_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '目标记录 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (actor_user_id, idempotency_key),
    INDEX               ix_demand_idempotency_target (action_code, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘需求幂等记录';

CREATE TABLE `matching_weight_profile`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    owner_user_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NULL COMMENT '业务负责人用户 ID',
    name               VARCHAR(120)                                      NOT NULL COMMENT '名称',
    status             VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    current_version_no INT                                               NOT NULL DEFAULT 0 COMMENT '当前业务版本号',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT ck_matching_weight_profile_default_owner CHECK (owner_user_id IS NOT NULL OR id='00000000000000000000000021'),
    CONSTRAINT ck_matching_weight_profile_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_matching_weight_profile_owner FOREIGN KEY (owner_user_id) REFERENCES identity_user_account (id) ON DELETE RESTRICT,
    INDEX              ix_matching_weight_profile_owner (owner_user_id, status, update_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配权重方案';

CREATE TABLE `matching_weight_profile_version`
(
    id                      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    profile_id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权重方案 ID',
    version_no              INT                                            NOT NULL COMMENT '业务版本号',
    skills_weight           SMALLINT                                       NOT NULL COMMENT '技能维度权重',
    role_weight             SMALLINT                                       NOT NULL COMMENT '角色维度权重',
    years_weight            SMALLINT                                       NOT NULL COMMENT '年限维度权重',
    responsibility_weight   SMALLINT                                       NOT NULL COMMENT '职责维度权重',
    industry_project_weight SMALLINT                                       NOT NULL COMMENT '行业项目维度权重',
    screening_settings_json JSON NULL COMMENT '随版本冻结的个人岗位附加条件与默认排序',
    `ext`                   TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                VARCHAR(255) NULL COMMENT '备注',
    `create_id`             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`           VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`           TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`           VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`           TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`               INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_matching_weight_profile_version UNIQUE (profile_id, version_no),
    CONSTRAINT ck_matching_weight_profile_weights CHECK (
        skills_weight BETWEEN 0 AND 100 AND role_weight BETWEEN 0 AND 100
            AND years_weight BETWEEN 0 AND 100 AND responsibility_weight BETWEEN 0 AND 100
            AND industry_project_weight BETWEEN 0 AND 100
            AND skills_weight + role_weight + years_weight + responsibility_weight + industry_project_weight = 100),
    CONSTRAINT fk_matching_weight_version_profile FOREIGN KEY (profile_id) REFERENCES matching_weight_profile (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配权重方案版本';

CREATE TABLE `matching_jd_match_plan`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    jd_version_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '职位描述（JD）版本 ID',
    name               VARCHAR(120)                                   NOT NULL COMMENT '名称',
    current_version_no INT                                            NOT NULL DEFAULT 0 COMMENT '当前业务版本号',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_matching_jd_match_plan_name UNIQUE (jd_version_id, name),
    CONSTRAINT fk_matching_plan_jd_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 匹配方案';

CREATE TABLE `matching_controlled_criterion_plan_version`
(
    id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    jd_version_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '职位描述（JD）版本 ID',
    version_no            INT                                            NOT NULL COMMENT '业务版本号',
    plan_max_bonus_points SMALLINT                                       NOT NULL COMMENT '方案允许的最大附加分',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT ck_matching_criterion_plan_bonus CHECK (plan_max_bonus_points BETWEEN 0 AND 30),
    CONSTRAINT fk_matching_criterion_plan_jd FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控条件方案版本';

CREATE TABLE `matching_jd_match_plan_version`
(
    id                                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    plan_id                              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '匹配方案 ID',
    jd_version_id                        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NULL COMMENT '职位描述（JD）版本 ID',
    version_no                           INT                                               NOT NULL COMMENT '业务版本号',
    status                               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    active_slot                          TINYINT NULL COMMENT '当前生效版本占位标记',
    controlled_criterion_plan_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '受控条件方案版本 ID',
    skills_enabled                       BOOLEAN                                           NOT NULL COMMENT '是否启用技能维度',
    role_enabled                         BOOLEAN                                           NOT NULL COMMENT '是否启用角色维度',
    years_enabled                        BOOLEAN                                           NOT NULL COMMENT '是否启用年限维度',
    responsibility_enabled               BOOLEAN                                           NOT NULL COMMENT '是否启用职责维度',
    industry_project_enabled             BOOLEAN                                           NOT NULL COMMENT '是否启用行业项目维度',
    `version`                            INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    configured_by_user_id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '配置操作人用户 ID',
    activated_by_user_id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '生效操作人用户 ID',
    activated_at                         TIMESTAMP(6) NULL COMMENT '生效时间',
    `ext`                                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                             VARCHAR(255) NULL COMMENT '备注',
    `create_id`                          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`                        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`                        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`                          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`                        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`                        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_matching_plan_version UNIQUE (plan_id, version_no),
    CONSTRAINT uk_matching_plan_active UNIQUE (jd_version_id, active_slot),
    CONSTRAINT ck_matching_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_matching_plan_active_slot CHECK ((status = 'ACTIVE' AND active_slot = 1) OR (status <> 'ACTIVE' AND active_slot IS NULL)),
    CONSTRAINT fk_matching_plan_version_plan FOREIGN KEY (plan_id) REFERENCES matching_jd_match_plan (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_plan_version_jd FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_plan_version_criteria FOREIGN KEY (controlled_criterion_plan_version_id) REFERENCES matching_controlled_criterion_plan_version (id) ON DELETE RESTRICT,
    INDEX                                ix_matching_plan_jd_time (jd_version_id, create_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 匹配方案版本';

CREATE TABLE `matching_jd_plan_binding`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '岗位版本 ID，一岗一个当前关联',
    plan_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '选择的评分版本；NULL 表示已解除关联',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    ext TEXT NULL COMMENT 'JSON 扩展信息',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT fk_matching_binding_jd FOREIGN KEY (id) REFERENCES demand_jd_version(id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_binding_plan FOREIGN KEY (plan_version_id) REFERENCES matching_jd_match_plan_version(id) ON DELETE RESTRICT,
    INDEX ix_matching_binding_plan (plan_version_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位版本与可复用评分方案的关联';

CREATE TABLE `matching_controlled_criterion_definition`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    plan_version_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '匹配方案版本 ID',
    criterion_code      VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '受控条件编码',
    level               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '条件满足级别',
    operator            VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作符',
    expected_value_json JSON                                              NOT NULL COMMENT '期望值（JSON）',
    bonus_points        SMALLINT                                          NOT NULL COMMENT '附加分值',
    reason              VARCHAR(500)                                      NOT NULL COMMENT '操作原因',
    evidence_policy     VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '证据使用策略',
    selectable_by_hr    BOOLEAN                                           NOT NULL COMMENT '是否允许 HR 选择',
    sequence_no         INT                                               NOT NULL COMMENT '顺序号',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_matching_criterion_fact UNIQUE (plan_version_id, criterion_code),
    CONSTRAINT uk_matching_criterion_sequence UNIQUE (plan_version_id, sequence_no),
    CONSTRAINT ck_matching_criterion_code CHECK (criterion_code IN ('AGE', 'GENDER', 'EDUCATION', 'CERTIFICATE', 'LANGUAGE')),
    CONSTRAINT ck_matching_criterion_level CHECK (level IN ('MUST', 'PREFER', 'DISPLAY_ONLY')),
    CONSTRAINT ck_matching_criterion_bonus CHECK ((level = 'PREFER' AND bonus_points IN (5, 10, 20)) OR (level <> 'PREFER' AND bonus_points = 0)),
    CONSTRAINT fk_matching_criterion_definition_plan FOREIGN KEY (plan_version_id) REFERENCES matching_controlled_criterion_plan_version (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控条件定义';

CREATE TABLE `matching_personal_prefer_selection_version`
(
    id                           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    owner_user_id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务负责人用户 ID',
    plan_version_id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配方案版本 ID',
    weight_profile_version_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权重方案版本 ID',
    version_no                   INT                                            NOT NULL COMMENT '业务版本号',
    selected_definition_ids_json JSON                                           NOT NULL COMMENT '已选条件定义 ID 列表（JSON）',
    `ext`                        TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                     VARCHAR(255) NULL COMMENT '备注',
    `create_id`                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`                VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`                TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`                VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`                TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`                    INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_matching_personal_selection_version UNIQUE (owner_user_id, plan_version_id, version_no),
    CONSTRAINT fk_matching_personal_selection_owner FOREIGN KEY (owner_user_id) REFERENCES identity_user_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_personal_selection_plan FOREIGN KEY (plan_version_id) REFERENCES matching_jd_match_plan_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_personal_selection_weight FOREIGN KEY (weight_profile_version_id) REFERENCES matching_weight_profile_version (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='个人匹配偏好版本';

CREATE TABLE `matching_ownership_history`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    aggregate_type     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合根类型',
    aggregate_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '聚合根 ID',
    from_owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '变更前负责人用户 ID',
    to_owner_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '变更后负责人用户 ID',
    reason             VARCHAR(500)                                      NOT NULL COMMENT '操作原因',
    actor_user_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际操作人用户 ID',
    occurred_at        TIMESTAMP(6)                                      NOT NULL COMMENT '事件发生时间',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX              ix_matching_ownership_aggregate (aggregate_type, aggregate_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配配置归属变更历史';

CREATE TABLE `matching_audit_event`
(
    id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    event_type      VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型',
    actor_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际操作人用户 ID',
    target_type     VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标类型',
    target_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '目标记录 ID',
    reason          VARCHAR(500) NULL COMMENT '操作原因',
    before_snapshot JSON NULL COMMENT '变更前数据快照（JSON）',
    after_snapshot  JSON NULL COMMENT '变更后数据快照（JSON）',
    occurred_at     TIMESTAMP(6)                                      NOT NULL COMMENT '事件发生时间',
    `ext`           TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`        VARCHAR(255) NULL COMMENT '备注',
    `create_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`   VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`   VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`       INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX           ix_matching_audit_target (target_type, target_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配业务审计事件';

CREATE TABLE `matching_idempotency_record`
(
    actor_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '实际操作人用户 ID',
    idempotency_key     VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等键',
    action_code         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '操作编码',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求指纹',
    target_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '目标记录 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (actor_user_id, idempotency_key),
    INDEX               ix_matching_idempotency_target (action_code, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配业务幂等记录';

CREATE TABLE `talent_source_channel`
(
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    code          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务编码',
    name          VARCHAR(120)                                      NOT NULL COMMENT '名称',
    channel_type  VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道类型',
    status        VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    `version`     INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_talent_source_channel_code UNIQUE (code),
    CONSTRAINT ck_talent_source_channel_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才来源渠道';

CREATE TABLE `talent_intake_batch`
(
    id                       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    source_channel_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '来源渠道 ID',
    source_recruiter_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '来源招聘顾问用户 ID',
    default_owner_user_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '默认负责人用户ID',
    owner_department_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务归属部门 ID',
    jd_version_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '上传时选择的岗位业务版本',
    status                   VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    total_count              INT                                               NOT NULL DEFAULT 0 COMMENT '总记录数',
    succeeded_count          INT                                               NOT NULL DEFAULT 0 COMMENT '成功记录数',
    failed_count             INT                                               NOT NULL DEFAULT 0 COMMENT '失败记录数',
    `ext`                    TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                 VARCHAR(255) NULL COMMENT '备注',
    `create_id`              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`            VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`            TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`            VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`            TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`                INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX ix_talent_batch_jd_version (jd_version_id),
    CONSTRAINT fk_talent_batch_channel FOREIGN KEY (source_channel_id) REFERENCES talent_source_channel (id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_batch_status CHECK (status IN ('OPEN', 'PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历接收批次';

CREATE TABLE `talent_resume_intake_processing`
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

CREATE TABLE `talent_resume_intake`
(
    id                       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    batch_id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '批次 ID',
    source_channel_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '来源渠道 ID',
    source_recruiter_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '来源招聘顾问用户 ID',
    owner_user_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '业务归属部门 ID',
    handled_by_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '处理人用户 ID',
    original_file_name       VARCHAR(255)                                       NOT NULL COMMENT '上传时的原始文件名',
    media_type               VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件媒体类型',
    size_bytes               BIGINT                                             NOT NULL COMMENT '文件大小（字节）',
    content_sha256           CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '内容 SHA-256 摘要',
    object_key               VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对象存储键',
    processing_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '共享解析处理 ID',
    scan_status              VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '安全扫描状态',
    status                   VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    failure_code             VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '失败编码',
    candidate_id             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '候选人 ID',
    resume_version_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '简历版本 ID',
    score_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关联岗位评分运行 ID',
    score_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '岗位评分状态',
    `version`                INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`                    TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                 VARCHAR(255) NULL COMMENT '备注',
    `create_id`              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`            VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`            TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`            VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`            TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    INDEX                    ix_talent_intake_batch (batch_id, create_time, id),
    INDEX                    ix_talent_intake_owner (owner_department_id, create_time, id),
    INDEX                    ix_talent_intake_hash (content_sha256),
    INDEX ix_talent_intake_processing (processing_id, id),
    CONSTRAINT fk_talent_intake_processing FOREIGN KEY (processing_id) REFERENCES talent_resume_intake_processing(id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_intake_batch FOREIGN KEY (batch_id) REFERENCES talent_intake_batch (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_intake_channel FOREIGN KEY (source_channel_id) REFERENCES talent_source_channel (id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_intake_status CHECK (status IN
                                              ('PENDING_IMPORT', 'READY_FOR_CONFIRMATION', 'CONFIRMED', 'IGNORED', 'REJECTED', 'QUARANTINED', 'FAILED_RETRYABLE', 'PARSING', 'IDENTITY_REVIEW_REQUIRED', 'COMPLETED')),
    CONSTRAINT ck_talent_intake_scan CHECK (scan_status IN ('CLEAN', 'MALICIOUS', 'UNAVAILABLE')),
    CONSTRAINT fk_talent_intake_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_intake_resume_version FOREIGN KEY (resume_version_id) REFERENCES talent_resume_version (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历接收记录';

CREATE TABLE `talent_candidate`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    full_name           VARCHAR(160)                                      NOT NULL COMMENT '姓名',
    primary_email       VARCHAR(320) NULL COMMENT '主要联系邮箱',
    primary_phone       VARCHAR(40) NULL COMMENT '主要联系电话',
    owner_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务归属部门 ID',
    status              VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    `version`           INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    INDEX ix_talent_candidate_page (update_time, id),
    CONSTRAINT ck_talent_candidate_status CHECK (status IN ('ACTIVE', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人主记录';

CREATE TABLE `talent_candidate_identity`
(
    id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    candidate_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '候选人 ID',
    identity_type         VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '身份类型',
    normalized_value_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '规范化值哈希',
    value_last_four       VARCHAR(8) NULL COMMENT '值最近后四位',
    verified              BOOLEAN                                           NOT NULL DEFAULT FALSE COMMENT '是否已验证',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_identity UNIQUE (identity_type, normalized_value_hash),
    CONSTRAINT fk_talent_identity_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_identity_type CHECK (identity_type IN ('EMAIL', 'PHONE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人身份标识';

CREATE TABLE `talent_resume`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    candidate_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    current_version_no INT                                            NOT NULL COMMENT '当前业务版本号',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_candidate UNIQUE (candidate_id),
    CONSTRAINT fk_talent_resume_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人简历主记录';

CREATE TABLE `talent_resume_version`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    resume_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '简历 ID',
    intake_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '接收ID',
    version_no         INT                                                NOT NULL COMMENT '业务版本号',
    original_file_name VARCHAR(255)                                       NOT NULL COMMENT '上传时的原始文件名',
    media_type         VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件媒体类型',
    size_bytes         BIGINT                                             NOT NULL COMMENT '文件大小（字节）',
    content_sha256     CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '内容 SHA-256 摘要',
    object_key         VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对象存储键',
    status             VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_version UNIQUE (resume_id, version_no),
    CONSTRAINT uk_talent_resume_intake UNIQUE (intake_id),
    INDEX ix_talent_resume_version_metric_time (create_time, id),
    CONSTRAINT fk_talent_resume_version_resume FOREIGN KEY (resume_id) REFERENCES talent_resume (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_version_intake FOREIGN KEY (intake_id) REFERENCES talent_resume_intake (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人简历版本';

CREATE TABLE `talent_identity_conflict`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    intake_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '接收ID',
    candidate_id_a      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '待合并候选人 A 的 ID',
    candidate_id_b      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '待合并候选人 B 的 ID',
    conflict_type       VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冲突类型',
    status              VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    resolution          VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '解决结果',
    chosen_candidate_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '选定候选人ID',
    reason              VARCHAR(500) NULL COMMENT '操作原因',
    resolved_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '解决操作人用户 ID',
    resolved_at         TIMESTAMP(6) NULL COMMENT '解决时间',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_talent_conflict_intake FOREIGN KEY (intake_id) REFERENCES talent_resume_intake (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_conflict_candidate_a FOREIGN KEY (candidate_id_a) REFERENCES talent_candidate (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_conflict_candidate_b FOREIGN KEY (candidate_id_b) REFERENCES talent_candidate (id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_conflict_status CHECK (status IN ('OPEN', 'RESOLVED')),
    INDEX               ix_talent_conflict_status (status, create_time, id),
    CONSTRAINT fk_talent_conflict_chosen_candidate
        FOREIGN KEY (chosen_candidate_id) REFERENCES talent_candidate (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人身份冲突记录';

CREATE TABLE `talent_download_authorization`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    token_hash         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '访问令牌哈希值',
    resume_version_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '简历版本 ID',
    authorized_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被授权用户 ID',
    expires_at         TIMESTAMP(6)                                   NOT NULL COMMENT '过期时间',
    consumed_at        TIMESTAMP(6) NULL COMMENT '消费时间',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_download_token UNIQUE (token_hash),
    CONSTRAINT fk_talent_download_resume_version FOREIGN KEY (resume_version_id) REFERENCES talent_resume_version (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历下载授权';

CREATE TABLE `talent_process_event`
(
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    event_type    VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型',
    actor_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际操作人用户 ID',
    target_type   VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标类型',
    target_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '目标记录 ID',
    reason        VARCHAR(500) NULL COMMENT '操作原因',
    occurred_at   TIMESTAMP(6)                                      NOT NULL COMMENT '事件发生时间',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX         ix_talent_process_target (target_type, target_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才处理事件';

CREATE TABLE `talent_idempotency_record`
(
    actor_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '实际操作人用户 ID',
    idempotency_key     VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等键',
    action_code         VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '操作编码',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求指纹',
    target_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '目标记录 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (actor_user_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才模块幂等记录';

CREATE TABLE `talent_candidate_merge_history`
(
    id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    survivor_candidate_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '合并后保留的候选人 ID',
    merged_candidate_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被合并的候选人 ID',
    identity_conflict_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '身份冲突ID',
    survivor_version      BIGINT                                         NOT NULL COMMENT '保留版本',
    merged_version        BIGINT                                         NOT NULL COMMENT '被合并版本',
    operated_by_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
    reason                VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_merge_merged_candidate UNIQUE (merged_candidate_id),
    CONSTRAINT fk_talent_merge_survivor FOREIGN KEY (survivor_candidate_id)
        REFERENCES talent_candidate (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_merge_merged FOREIGN KEY (merged_candidate_id)
        REFERENCES talent_candidate (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_merge_conflict FOREIGN KEY (identity_conflict_id)
        REFERENCES talent_identity_conflict (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人合并历史';

CREATE TABLE `reporting_async_task`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    trace_id             VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建任务时的请求关联 ID，历史任务可为空',
    task_type            VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '任务类型',
    resource_type        VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '资源类型',
    resource_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '资源 ID',
    requested_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求发起人用户 ID',
    owner_user_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '业务归属部门 ID',
    view_permission      VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '查看权限',
    manage_permission    VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '管理权限',
    status               VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    attempt_count        INT                                                NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    max_attempts         INT                                                NOT NULL COMMENT '最大尝试次数',
    progress_percent     INT                                                NOT NULL DEFAULT 0 COMMENT '任务进度百分比',
    error_code           VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    safe_error_detail    VARCHAR(500) NULL COMMENT '可安全展示的错误详情',
    retryable            BOOLEAN                                            NOT NULL DEFAULT FALSE COMMENT '失败后是否允许重试',
    result_resource_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '结果资源 ID',
    idempotency_key      VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等键',
    request_fingerprint  CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求指纹',
    `version`            INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    started_at           TIMESTAMP(6) NULL COMMENT '开始处理时间',
    lease_until          TIMESTAMP(6) NULL COMMENT '当前处理租约到期时间',
    next_retry_at        TIMESTAMP(6) NULL COMMENT '延迟重试最早领取时间，空值表示立即可领',
    finished_at          TIMESTAMP(6) NULL COMMENT '完成处理时间',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_reporting_task_idempotency UNIQUE (requested_by_user_id, idempotency_key),
    INDEX ix_reporting_task_resource (task_type, resource_id, create_time, id),
    INDEX                ix_reporting_task_claim (task_type, status, create_time, id),
    INDEX                ix_reporting_task_lease (task_type, status, lease_until, id),
    INDEX                ix_reporting_task_retry (task_type, status, next_retry_at, create_time, id),
    CONSTRAINT ck_reporting_task_status CHECK (status IN (
                                                          'QUEUED', 'RUNNING', 'CANCELLING', 'CANCELLED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_reporting_task_progress CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_reporting_task_attempts CHECK (attempt_count >= 0 AND max_attempts BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表异步任务';

CREATE TABLE `reporting_outbox_event`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '事件及独立发布任务 ID',
    work_task_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被通知的持久工作任务 ID',
    work_task_type      VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工作任务类型及消息路由键',
    event_type          VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件协议类型',
    event_version       INT NOT NULL COMMENT '事件协议版本',
    aggregate_type      VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务聚合类型',
    aggregate_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务聚合 ID',
    occurred_at         TIMESTAMP(6) NOT NULL COMMENT '原始业务事件发生时间',
    trace_id            VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '安全链路关联 ID',
    actor_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原始操作者用户 ID 快照',
    owner_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建时任务所属用户 ID 快照',
    owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建时任务所属部门 ID 快照',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_reporting_outbox_work UNIQUE (work_task_id),
    CONSTRAINT fk_reporting_outbox_publication FOREIGN KEY (id) REFERENCES reporting_async_task (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reporting_outbox_work FOREIGN KEY (work_task_id) REFERENCES reporting_async_task (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reporting_outbox_protocol CHECK (event_version > 0),
    CONSTRAINT ck_reporting_outbox_distinct_tasks CHECK (id <> work_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='与业务事实同事务提交的持久任务通知信封';

CREATE TABLE `reporting_search_task_recovery`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '新恢复任务 ID',
    original_task_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原失败任务 ID',
    outbox_event_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '恢复任务使用的持久事件 ID',
    reason               VARCHAR(500) NOT NULL COMMENT '管理员填写的恢复原因',
    requested_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际创建恢复的管理员 ID',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`            INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_search_recovery_original(original_task_id),
    INDEX ix_reporting_search_recovery_event(outbox_event_id),
    CONSTRAINT fk_reporting_search_recovery_new FOREIGN KEY(id) REFERENCES reporting_async_task(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reporting_search_recovery_original FOREIGN KEY(original_task_id) REFERENCES reporting_async_task(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reporting_search_recovery_event FOREIGN KEY(outbox_event_id) REFERENCES reporting_outbox_event(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索任务人工恢复关联';

CREATE TABLE `reporting_async_task_attempt`
(
    id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    task_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '异步任务 ID',
    attempt_no        INT                                               NOT NULL COMMENT '尝试序号',
    status            VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    error_code        VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    safe_error_detail VARCHAR(500) NULL COMMENT '可安全展示的错误详情',
    started_at        TIMESTAMP(6)                                      NOT NULL COMMENT '开始处理时间',
    finished_at       TIMESTAMP(6) NULL COMMENT '完成处理时间',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_reporting_task_attempt UNIQUE (task_id, attempt_no),
    CONSTRAINT fk_reporting_task_attempt_task FOREIGN KEY (task_id)
        REFERENCES reporting_async_task (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reporting_task_attempt_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表异步任务尝试记录';

CREATE TABLE `talent_resume_extraction_run`
(
    id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    resume_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '简历版本 ID',
    task_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '异步任务 ID',
    extractor_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '抽取器版本，抽取完成后回填',
    route             VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '路径',
    status            VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    page_count        INT                                               NOT NULL COMMENT '页数',
    successful_pages  INT                                               NOT NULL COMMENT '成功PAGES',
    fallback_pages    INT                                               NOT NULL COMMENT '降级PAGES',
    printable_ratio   DECIMAL(8, 6)                                     NOT NULL COMMENT '可打印字符占比',
    coverage_ratio    DECIMAL(8, 6)                                     NOT NULL COMMENT '文本覆盖率',
    error_code        VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    finished_at       TIMESTAMP(6) NULL COMMENT '完成处理时间',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX             ix_talent_resume_extraction_version (resume_version_id, create_time, id),
    CONSTRAINT fk_talent_resume_extraction_version FOREIGN KEY (resume_version_id)
        REFERENCES talent_resume_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_extraction_task FOREIGN KEY (task_id)
        REFERENCES reporting_async_task (id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_resume_extraction_route CHECK (route IN ('NATIVE_TEXT', 'OCR_TEXT', 'MULTIMODAL', 'HYBRID', 'UNAVAILABLE')),
    CONSTRAINT ck_talent_resume_extraction_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历文本抽取任务';

CREATE TABLE `talent_resume_extraction_page`
(
    id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    extraction_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '文本抽取任务 ID',
    page_no           INT                                               NOT NULL COMMENT '页码',
    route             VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '路径',
    extracted_text    MEDIUMTEXT                                        NOT NULL COMMENT '当前页抽取文本',
    character_count   INT                                               NOT NULL COMMENT '字符数量',
    printable_ratio   DECIMAL(8, 6)                                     NOT NULL COMMENT '可打印字符占比',
    quality_score     DECIMAL(8, 6)                                     NOT NULL COMMENT '数据质量评分',
    content_hash      CHAR(64) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '内容哈希值',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_extraction_page UNIQUE (extraction_run_id, page_no),
    CONSTRAINT fk_talent_resume_extraction_page_run FOREIGN KEY (extraction_run_id)
        REFERENCES talent_resume_extraction_run (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历逐页文本抽取结果';

CREATE TABLE `talent_resume_ai_model_run`
(
    id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    resume_version_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '简历版本 ID',
    extraction_run_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '文本抽取任务 ID',
    schema_version        VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '数据结构版本号',
    prompt_version        VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '提示词模板版本',
    model_provider        VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin  NULL COMMENT '模型服务提供方，模型调用后回填',
    model_version         VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型版本标识，模型调用后回填',
    status                VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    validation_status     VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '数据校验状态',
    raw_output_object_key VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型原始输出的对象存储键',
    raw_output_hash       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型原始输出哈希',
    raw_output_size       BIGINT NULL COMMENT '模型原始输出大小（字节）',
    encryption_version    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '加密版本',
    input_tokens          INT                                                NOT NULL DEFAULT 0 COMMENT '模型输入 Token 数',
    output_tokens         INT                                                NOT NULL DEFAULT 0 COMMENT '模型输出 Token 数',
    duration_millis       BIGINT                                             NOT NULL DEFAULT 0 COMMENT '处理耗时（毫秒）',
    error_code            VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    finished_at           TIMESTAMP(6) NULL COMMENT '完成处理时间',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX                 ix_talent_resume_model_version (resume_version_id, create_time, id),
    CONSTRAINT fk_talent_resume_model_version FOREIGN KEY (resume_version_id)
        REFERENCES talent_resume_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_model_extraction FOREIGN KEY (extraction_run_id)
        REFERENCES talent_resume_extraction_run (id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_resume_model_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_talent_resume_model_validation CHECK (validation_status IN
                                                        ('PENDING', 'VALID', 'SCHEMA_INVALID', 'EVIDENCE_INVALID', 'MODEL_UNAVAILABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历解析模型运行记录';

CREATE TABLE `talent_resume_parse_snapshot`
(
    id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    resume_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '简历版本 ID',
    extraction_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '文本抽取任务 ID',
    model_run_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '模型运行记录 ID',
    schema_version    VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据结构版本号',
    prompt_version    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提示词模板版本',
    cache_key         CHAR(64) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '解析结果缓存键',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_parse_cache UNIQUE (cache_key),
    INDEX ix_talent_parse_snapshot_metric_time (create_time, id),
    CONSTRAINT fk_talent_resume_parse_version FOREIGN KEY (resume_version_id)
        REFERENCES talent_resume_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_parse_extraction FOREIGN KEY (extraction_run_id)
        REFERENCES talent_resume_extraction_run (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_parse_model FOREIGN KEY (model_run_id)
        REFERENCES talent_resume_ai_model_run (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历解析结果快照';

CREATE TABLE `talent_resume_fact_snapshot`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    resume_version_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '简历版本 ID',
    parse_snapshot_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '解析快照 ID',
    previous_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '上一快照ID',
    snapshot_version     INT                                               NOT NULL COMMENT '快照版本',
    source_type          VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据来源类型',
    current_marker       CHAR(1) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前记录唯一标记',
    full_name            VARCHAR(160) NULL COMMENT '姓名',
    email                VARCHAR(320) NULL COMMENT '邮箱地址',
    phone                VARCHAR(40) NULL COMMENT '联系电话',
    current_role         VARCHAR(160) NULL COMMENT '当前角色',
    city                 VARCHAR(120) NULL COMMENT '所在城市',
    age                  INT NULL COMMENT '年龄（仅保存明确证据）',
    gender               VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '性别（仅保存明确证据）',
    job_intention        VARCHAR(300) NULL COMMENT '求职意向',
    corrected_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更正操作人用户 ID',
    correction_reason    VARCHAR(500) NULL COMMENT '更正原因',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`            INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_fact_version UNIQUE (resume_version_id, snapshot_version),
    CONSTRAINT uk_talent_resume_fact_current UNIQUE (resume_version_id, current_marker),
    CONSTRAINT fk_talent_resume_fact_version FOREIGN KEY (resume_version_id)
        REFERENCES talent_resume_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_fact_parse FOREIGN KEY (parse_snapshot_id)
        REFERENCES talent_resume_parse_snapshot (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_resume_fact_previous FOREIGN KEY (previous_snapshot_id)
        REFERENCES talent_resume_fact_snapshot (id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_resume_fact_source CHECK (source_type IN ('AI_PARSE', 'HUMAN_CORRECTION')),
    CONSTRAINT ck_talent_resume_fact_current CHECK (current_marker IS NULL OR current_marker = 'Y'),
    CONSTRAINT ck_talent_resume_fact_age CHECK (age IS NULL OR age BETWEEN 16 AND 100),
    CONSTRAINT ck_talent_resume_fact_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历事实快照';

CREATE TABLE `talent_resume_work_fact`
(
    id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    fact_snapshot_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '事实快照 ID',
    item_key              VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任务条目业务键',
    employer              VARCHAR(200) NULL COMMENT '任职公司名称',
    role_name             VARCHAR(160) NULL COMMENT '角色名称',
    start_date            DATE NULL COMMENT '开始日期',
    end_date              DATE NULL COMMENT '结束日期',
    description_text      TEXT NULL COMMENT '描述文本',
    responsibilities_json JSON                                              NOT NULL COMMENT '岗位职责列表（JSON）',
    achievements_json     JSON                                              NOT NULL COMMENT '工作成果列表（JSON）',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_work_item UNIQUE (fact_snapshot_id, item_key),
    CONSTRAINT fk_talent_resume_work_snapshot FOREIGN KEY (fact_snapshot_id)
        REFERENCES talent_resume_fact_snapshot (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历工作经历事实';

CREATE TABLE `talent_resume_project_fact`
(
    id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    fact_snapshot_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '事实快照 ID',
    item_key          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任务条目业务键',
    project_name      VARCHAR(200) NULL COMMENT '项目名称',
    role_name         VARCHAR(160) NULL COMMENT '角色名称',
    start_date        DATE NULL COMMENT '开始日期',
    end_date          DATE NULL COMMENT '结束日期',
    description_text  TEXT NULL COMMENT '描述文本',
    technologies_json JSON                                              NOT NULL COMMENT '技术栈列表（JSON）',
    achievements_json JSON                                              NOT NULL COMMENT '工作成果列表（JSON）',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_project_item UNIQUE (fact_snapshot_id, item_key),
    CONSTRAINT fk_talent_resume_project_snapshot FOREIGN KEY (fact_snapshot_id)
        REFERENCES talent_resume_fact_snapshot (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历项目经历事实';

CREATE TABLE `talent_resume_education_fact`
(
    id               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    fact_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '事实快照 ID',
    item_key         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任务条目业务键',
    school           VARCHAR(200) NULL COMMENT '学校名称',
    degree_name      VARCHAR(120) NULL COMMENT '学历或学位名称',
    major            VARCHAR(160) NULL COMMENT '专业名称',
    start_date       DATE NULL COMMENT '开始日期',
    end_date         DATE NULL COMMENT '结束日期',
    learning_mode    VARCHAR(80) NULL COMMENT '学习形式',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`        INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_education_item UNIQUE (fact_snapshot_id, item_key),
    CONSTRAINT fk_talent_resume_education_snapshot FOREIGN KEY (fact_snapshot_id)
        REFERENCES talent_resume_fact_snapshot (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历教育经历事实';

CREATE TABLE `talent_resume_skill_fact`
(
    id               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    fact_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '事实快照 ID',
    item_key         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任务条目业务键',
    skill_name       VARCHAR(120)                                      NOT NULL COMMENT '技能名称',
    level_name       VARCHAR(80) NULL COMMENT '等级名称',
    years_value      DECIMAL(6, 2) NULL COMMENT '年限值',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`        INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_skill_item UNIQUE (fact_snapshot_id, item_key),
    CONSTRAINT fk_talent_resume_skill_snapshot FOREIGN KEY (fact_snapshot_id)
        REFERENCES talent_resume_fact_snapshot (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历技能事实';

CREATE TABLE `talent_resume_simple_fact`
(
    id               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    fact_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '事实快照 ID',
    fact_type        VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事实类型',
    item_key         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任务条目业务键',
    name_value       VARCHAR(300)                                      NOT NULL COMMENT '名称值',
    level_value      VARCHAR(120) NULL COMMENT '等级值',
    issuer_value     VARCHAR(200) NULL COMMENT '签发方值',
    start_date       DATE NULL COMMENT '开始日期',
    end_date         DATE NULL COMMENT '结束日期',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`        INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_talent_resume_simple_item UNIQUE (fact_snapshot_id, fact_type, item_key),
    CONSTRAINT fk_talent_resume_simple_snapshot FOREIGN KEY (fact_snapshot_id)
        REFERENCES talent_resume_fact_snapshot (id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_resume_simple_type CHECK (fact_type IN ('CREDENTIAL', 'LANGUAGE', 'INDUSTRY_TAG', 'RESPONSIBILITY_TAG', 'UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历简单属性事实';

CREATE TABLE `talent_resume_fact_evidence`
(
    id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    fact_snapshot_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '事实快照 ID',
    json_pointer      VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结构化数据中的 JSON Pointer 路径',
    page_no           INT                                                NOT NULL COMMENT '页码',
    start_offset      INT                                                NOT NULL COMMENT '证据起始字符偏移量',
    end_offset        INT                                                NOT NULL COMMENT '证据结束字符偏移量',
    quote_text        VARCHAR(1000)                                      NOT NULL COMMENT '证据原文摘录',
    bounding_box_json JSON                                               NOT NULL COMMENT '证据在页面中的坐标（JSON）',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_talent_resume_evidence_snapshot FOREIGN KEY (fact_snapshot_id)
        REFERENCES talent_resume_fact_snapshot (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历事实证据';

CREATE TABLE `talent_fact_correction`
(
    id                         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    resume_version_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '简历版本 ID',
    previous_fact_snapshot_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上一事实快照ID',
    new_fact_snapshot_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '新事实快照ID',
    corrected_by_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '更正操作人用户 ID',
    reason                     VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    affected_capabilities_json JSON                                           NOT NULL COMMENT '受影响能力（JSON）',
    `ext`                      TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                   VARCHAR(255) NULL COMMENT '备注',
    `create_id`                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`              VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`              TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`              VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`              TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`                  INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_talent_correction_version FOREIGN KEY (resume_version_id)
        REFERENCES talent_resume_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_correction_previous FOREIGN KEY (previous_fact_snapshot_id)
        REFERENCES talent_resume_fact_snapshot (id) ON DELETE RESTRICT,
    CONSTRAINT fk_talent_correction_new FOREIGN KEY (new_fact_snapshot_id)
        REFERENCES talent_resume_fact_snapshot (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历事实人工更正记录';

CREATE TABLE `talent_fact_correction_change`
(
    id             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    correction_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '更正ID',
    json_pointer   VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结构化数据中的 JSON Pointer 路径',
    old_value_json JSON NULL COMMENT '原值（JSON）',
    new_value_json JSON NULL COMMENT '新值（JSON）',
    source_type    VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '数据来源类型',
    `ext`          TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`       VARCHAR(255) NULL COMMENT '备注',
    `create_id`    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`  VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`  TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`  VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`  TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`      INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_talent_correction_change FOREIGN KEY (correction_id)
        REFERENCES talent_fact_correction (id) ON DELETE RESTRICT,
    CONSTRAINT ck_talent_correction_source CHECK (source_type IN ('RESUME_EVIDENCE', 'HUMAN_PROVIDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='简历事实人工更正明细';

CREATE TABLE `demand_jd_ai_model_run`
(
    id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    jd_version_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '职位描述（JD）版本 ID',
    task_id               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '异步任务 ID',
    schema_version        VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '数据结构版本号',
    prompt_version        VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '提示词模板版本',
    model_provider        VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin  NULL COMMENT '模型服务提供方，模型调用后回填',
    model_version         VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型版本标识，模型调用后回填',
    status                VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    validation_status     VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '数据校验状态',
    raw_output_object_key VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型原始输出的对象存储键',
    raw_output_hash       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模型原始输出哈希',
    raw_output_size       BIGINT NULL COMMENT '模型原始输出大小（字节）',
    encryption_version    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '加密版本',
    input_tokens          INT                                                NOT NULL DEFAULT 0 COMMENT '模型输入 Token 数',
    output_tokens         INT                                                NOT NULL DEFAULT 0 COMMENT '模型输出 Token 数',
    duration_millis       BIGINT                                             NOT NULL DEFAULT 0 COMMENT '处理耗时（毫秒）',
    error_code            VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    finished_at           TIMESTAMP(6) NULL COMMENT '完成处理时间',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX                 ix_demand_jd_model_version (jd_version_id,create_time,id),
    CONSTRAINT fk_demand_jd_model_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_demand_jd_model_task FOREIGN KEY (task_id) REFERENCES reporting_async_task (id) ON DELETE RESTRICT,
    CONSTRAINT ck_demand_jd_model_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_demand_jd_model_validation CHECK (validation_status IN
                                                    ('PENDING', 'VALID', 'SCHEMA_INVALID', 'EVIDENCE_INVALID', 'MODEL_UNAVAILABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 解析模型运行记录';

CREATE TABLE `demand_jd_fact_snapshot`
(
    id               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    jd_version_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '职位描述（JD）版本 ID',
    model_run_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '模型运行记录 ID',
    snapshot_version INT                                               NOT NULL COMMENT '快照版本',
    source_type      VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据来源类型',
    current_marker   CHAR(1) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前记录唯一标记',
    schema_version   VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据结构版本号',
    facts_json       JSON                                              NOT NULL COMMENT '结构化事实内容（JSON）',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`        INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_demand_jd_fact_version UNIQUE (jd_version_id, snapshot_version),
    CONSTRAINT uk_demand_jd_fact_current UNIQUE (jd_version_id, current_marker),
    CONSTRAINT fk_demand_jd_fact_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_demand_jd_fact_model FOREIGN KEY (model_run_id) REFERENCES demand_jd_ai_model_run (id) ON DELETE RESTRICT,
    CONSTRAINT ck_demand_jd_fact_source CHECK (source_type IN ('AI_PARSE', 'HUMAN_CORRECTION')),
    CONSTRAINT ck_demand_jd_fact_current CHECK (current_marker IS NULL OR current_marker = 'Y')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 事实快照';

CREATE TABLE `matching_candidate_selection_snapshot`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    jd_version_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '职位描述（JD）版本 ID',
    requested_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求发起人用户 ID',
    item_count           INT                                            NOT NULL COMMENT '条目总数',
    selection_hash       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选范围选择哈希',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`            INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT ck_matching_selection_count CHECK (item_count BETWEEN 1 AND 5000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配候选范围快照';

CREATE TABLE `matching_candidate_selection_item`
(
    selection_snapshot_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人选择快照 ID',
    sequence_no             INT                                            NOT NULL COMMENT '顺序号',
    candidate_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    resume_version_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '简历版本 ID',
    resume_fact_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '简历事实快照 ID',
    `ext`                   TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                VARCHAR(255) NULL COMMENT '备注',
    `create_id`             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`           VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`           TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`           VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`           TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`               INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (selection_snapshot_id, sequence_no),
    CONSTRAINT uk_matching_selection_resume UNIQUE (selection_snapshot_id, resume_version_id),
    CONSTRAINT fk_matching_selection_item FOREIGN KEY (selection_snapshot_id) REFERENCES matching_candidate_selection_snapshot (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配候选范围条目';

CREATE TABLE `matching_match_run`
(
    id                             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    task_id                        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '异步任务 ID',
    jd_version_id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '职位描述（JD）版本 ID',
    jd_fact_snapshot_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT 'JD 事实快照 ID',
    plan_version_id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '匹配方案版本 ID',
    criterion_plan_version_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '受控条件方案版本 ID',
    weight_profile_version_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '权重方案版本 ID',
    personal_preference_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '个人偏好版本 ID',
    run_purpose                    VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CUSTOM' COMMENT 'COMPANY_DEFAULT公司默认评分；CUSTOM用户自定义筛选',
    selection_snapshot_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '候选人选择快照 ID',
    selected_criterion_ids_json    JSON                                              NOT NULL COMMENT '已选受控条件 ID 列表（JSON）',
    sort_mode                      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结果排序模式',
    status                         VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    total_count                    INT                                               NOT NULL COMMENT '总记录数',
    succeeded_count                INT                                               NOT NULL DEFAULT 0 COMMENT '成功记录数',
    failed_count                   INT                                               NOT NULL DEFAULT 0 COMMENT '失败记录数',
    owner_user_id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务归属部门 ID',
    error_code                     VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    `version`                      INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    started_at                     TIMESTAMP(6) NULL COMMENT '开始处理时间',
    finished_at                    TIMESTAMP(6) NULL COMMENT '完成处理时间',
    `ext`                          TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                       VARCHAR(255) NULL COMMENT '备注',
    `create_id`                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`                  VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`                  TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`                  VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`                  TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_matching_run_task UNIQUE (task_id),
    CONSTRAINT ck_matching_run_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_matching_run_purpose CHECK (run_purpose IN ('COMPANY_DEFAULT', 'CUSTOM')),
    CONSTRAINT ck_matching_run_sort CHECK (sort_mode IN ('REFERENCE_SCORE', 'PROFESSIONAL_SCORE', 'BONUS_SCORE')),
    CONSTRAINT fk_matching_run_selection FOREIGN KEY (selection_snapshot_id) REFERENCES matching_candidate_selection_snapshot (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_run_plan FOREIGN KEY (plan_version_id) REFERENCES matching_jd_match_plan_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_run_weight FOREIGN KEY (weight_profile_version_id) REFERENCES matching_weight_profile_version (id) ON DELETE RESTRICT,
    INDEX                          ix_matching_run_owner (owner_user_id,create_time DESC,id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配运行任务';

CREATE TABLE `matching_match_run_attempt`
(
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    match_run_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '匹配运行任务 ID',
    attempt_no    INT                                               NOT NULL COMMENT '尝试序号',
    status        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    error_code    VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    started_at    TIMESTAMP(6)                                      NOT NULL COMMENT '开始处理时间',
    finished_at   TIMESTAMP(6) NULL COMMENT '完成处理时间',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_matching_run_attempt UNIQUE (match_run_id, attempt_no),
    CONSTRAINT fk_matching_attempt_run FOREIGN KEY (match_run_id) REFERENCES matching_match_run (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配运行尝试记录';

CREATE TABLE `matching_match_run_item_outcome`
(
    match_run_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '匹配运行任务 ID',
    candidate_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '候选人 ID',
    resume_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '简历版本 ID',
    status            VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    error_code        VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    last_attempt_no   INT                                               NOT NULL COMMENT '最近尝试序号',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (match_run_id, candidate_id),
    CONSTRAINT ck_matching_item_outcome_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT fk_matching_item_outcome_run FOREIGN KEY (match_run_id) REFERENCES matching_match_run (id) ON DELETE RESTRICT,
    INDEX             ix_matching_item_outcome_retry (match_run_id,status,resume_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配运行条目结果';

CREATE TABLE `matching_base_evaluation`
(
    id                        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    cache_key                 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '解析结果缓存键',
    jd_fact_snapshot_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT 'JD 事实快照 ID',
    resume_fact_snapshot_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '简历事实快照 ID',
    plan_version_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '匹配方案版本 ID',
    weight_profile_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '权重方案版本 ID',
    selected_criterion_hash   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '受控条件选择哈希',
    evaluated_on              DATE                                              NOT NULL COMMENT '评估ON',
    professional_score        DECIMAL(7, 2)                                     NOT NULL COMMENT '专业匹配得分',
    bonus_score               SMALLINT                                          NOT NULL COMMENT '附加项得分',
    reference_score           DECIMAL(7, 2)                                     NOT NULL COMMENT '综合参考分',
    must_tier                 VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MUST 条件分层结果',
    selected_bonus_cap        SMALLINT                                          NOT NULL COMMENT '本次选择的附加分上限',
    dimensions_json           JSON                                              NOT NULL COMMENT '各评分维度结果（JSON）',
    criteria_json             JSON                                              NOT NULL COMMENT '筛选条件（JSON）',
    algorithm_version         VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配算法版本号',
    `ext`                     TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                  VARCHAR(255) NULL COMMENT '备注',
    `create_id`               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`             VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`             TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`             VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`             TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`                 INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_matching_base_cache UNIQUE (cache_key),
    CONSTRAINT ck_matching_base_must CHECK (must_tier IN ('PASS', 'UNKNOWN', 'FAIL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础匹配评估结果';

CREATE TABLE `matching_evaluation_evidence`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    base_evaluation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '基础评估ID',
    source_type        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据来源类型',
    json_pointer       VARCHAR(500)                                      NOT NULL COMMENT '结构化数据中的 JSON Pointer 路径',
    quote_text         VARCHAR(1000)                                     NOT NULL COMMENT '证据原文摘录',
    page_no            INT NULL COMMENT '页码',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_matching_evidence_base FOREIGN KEY (base_evaluation_id) REFERENCES matching_base_evaluation (id) ON DELETE RESTRICT,
    INDEX              ix_matching_evidence_base (base_evaluation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配评估证据';

CREATE TABLE `matching_highlight`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    base_evaluation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '基础评估ID',
    dimension_code     VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评分维度编码',
    highlight_text     VARCHAR(500)                                      NOT NULL COMMENT '匹配亮点文本',
    sequence_no        INT                                               NOT NULL COMMENT '顺序号',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_matching_highlight_base FOREIGN KEY (base_evaluation_id) REFERENCES matching_base_evaluation (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配亮点';

CREATE TABLE `matching_criterion_evaluation`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    base_evaluation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '基础评估ID',
    definition_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '定义ID',
    criterion_code     VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '受控条件编码',
    level              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '条件满足级别',
    evaluation_status  VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评估状态',
    selected           BOOLEAN                                           NOT NULL COMMENT '是否已选中',
    configured_bonus   SMALLINT                                          NOT NULL COMMENT '配置附加分',
    awarded_bonus      SMALLINT                                          NOT NULL COMMENT '实际授予附加分',
    actual_json        JSON                                              NOT NULL COMMENT '实际（JSON）',
    expected_json      JSON                                              NOT NULL COMMENT '期望（JSON）',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_matching_criterion_base FOREIGN KEY (base_evaluation_id) REFERENCES matching_base_evaluation (id) ON DELETE RESTRICT,
    CONSTRAINT ck_matching_criterion_status CHECK (evaluation_status IN ('PASS', 'FAIL', 'UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控条件评估结果';

CREATE TABLE `matching_weighted_result`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    match_run_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '匹配运行任务 ID',
    base_evaluation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '基础评估ID',
    candidate_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '候选人 ID',
    resume_version_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '简历版本 ID',
    professional_score DECIMAL(7, 2)                                     NOT NULL COMMENT '专业匹配得分',
    bonus_score        SMALLINT                                          NOT NULL COMMENT '附加项得分',
    reference_score    DECIMAL(7, 2)                                     NOT NULL COMMENT '综合参考分',
    must_tier          VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MUST 条件分层结果',
    stable_rank        INT NULL COMMENT '稳定排序名次',
    result_json        JSON                                              NOT NULL COMMENT '处理结果（JSON）',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_matching_run_candidate UNIQUE (match_run_id, candidate_id),
    CONSTRAINT fk_matching_result_run FOREIGN KEY (match_run_id) REFERENCES matching_match_run (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_result_base FOREIGN KEY (base_evaluation_id) REFERENCES matching_base_evaluation (id) ON DELETE RESTRICT,
    INDEX              ix_matching_result_rank (match_run_id,stable_rank,candidate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='加权匹配结果';

CREATE TABLE `matching_screening_admission`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    candidate_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '候选人 ID',
    resume_version_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '简历版本 ID',
    jd_version_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT 'JD 版本 ID',
    default_match_run_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '已完成的公司默认评分运行 ID',
    admitted_by_user_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际准入用户 ID',
    owner_department_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '准入时用户归属部门 ID',
    admitted_at          TIMESTAMP(6)                                      NOT NULL COMMENT '准入时间',
    returned_at          TIMESTAMP(6) NULL COMMENT '退回我的简历时间，空值表示仍在智能筛选',
    version              INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_matching_screening_identity UNIQUE (candidate_id, resume_version_id, jd_version_id),
    CONSTRAINT fk_matching_screening_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_screening_resume FOREIGN KEY (resume_version_id) REFERENCES talent_resume_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_screening_jd FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_matching_screening_default_run FOREIGN KEY (default_match_run_id) REFERENCES matching_match_run (id) ON DELETE RESTRICT,
    INDEX ix_matching_screening_actor (admitted_by_user_id, admitted_at DESC, id DESC),
    INDEX ix_matching_screening_run (default_match_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人工加入智能筛选的精确简历岗位版本';

CREATE TABLE `matching_ai_result_feedback`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    base_evaluation_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '基础评估ID',
    actor_user_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际操作人用户 ID',
    feedback_code      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '反馈编码',
    reason             VARCHAR(500)                                      NOT NULL COMMENT '操作原因',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_matching_feedback_base FOREIGN KEY (base_evaluation_id) REFERENCES matching_base_evaluation (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 匹配结果反馈';

CREATE TABLE `search_filter_template`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    owner_user_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务负责人用户 ID',
    name               VARCHAR(120)                                      NOT NULL COMMENT '名称',
    status             VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    default_marker     CHAR(1) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '默认记录唯一标记',
    current_version_no INT                                               NOT NULL COMMENT '当前业务版本号',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_search_template_owner_name UNIQUE (owner_user_id, name),
    CONSTRAINT uk_search_template_owner_default UNIQUE (owner_user_id, default_marker),
    CONSTRAINT ck_search_template_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_search_template_default CHECK (default_marker IS NULL OR default_marker = 'Y')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才搜索筛选模板';

CREATE TABLE `search_filter_template_version`
(
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    template_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模板 ID',
    version_no    INT                                            NOT NULL COMMENT '业务版本号',
    query_json    JSON                                           NOT NULL COMMENT '查询条件（JSON）',
    change_reason VARCHAR(500)                                   NOT NULL COMMENT '变更原因',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_search_template_version UNIQUE (template_id, version_no),
    CONSTRAINT fk_search_template_version_root FOREIGN KEY (template_id) REFERENCES search_filter_template (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才搜索筛选模板版本';

CREATE TABLE `search_filter_template_share`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    template_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '模板 ID',
    template_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '模板版本 ID',
    subject_type        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权主体类型',
    subject_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '授权主体 ID',
    status              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    shared_by_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '共享操作人用户 ID',
    share_reason        VARCHAR(500)                                      NOT NULL COMMENT '共享原因',
    revoked_at          TIMESTAMP(6) NULL COMMENT '撤销时间',
    revoked_by_user_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '撤销操作人用户 ID',
    revoke_reason       VARCHAR(500) NULL COMMENT '撤销原因',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT ck_search_template_share_subject CHECK (subject_type IN ('USER', 'DEPARTMENT', 'COMPANY')),
    CONSTRAINT ck_search_template_share_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT fk_search_template_share_root FOREIGN KEY (template_id) REFERENCES search_filter_template (id) ON DELETE RESTRICT,
    CONSTRAINT fk_search_template_share_version FOREIGN KEY (template_version_id) REFERENCES search_filter_template_version (id) ON DELETE RESTRICT,
    INDEX               ix_search_template_share_lookup (subject_type,subject_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才搜索筛选模板共享记录';

CREATE TABLE `search_projection_checkpoint`
(
    projection_name  VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '搜索投影名称',
    index_name       VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前游标对应的实际搜索索引，空值需要重新绑定并投影',
    last_created_at  TIMESTAMP(6)                                   NOT NULL COMMENT '最近已投影记录的创建时间',
    last_snapshot_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近已投影快照 ID',
    projected_count  BIGINT                                         NOT NULL DEFAULT 0 COMMENT '预计数量',
    `version`        INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才搜索投影检查点';

CREATE TABLE `search_state_replay_checkpoint`
(
    projection_name  VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '搜索投影名称',
    index_name       VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前游标对应的实际搜索索引，空值需要重新绑定并投影',
    last_candidate_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近已回放候选人 ID',
    completed       BOOLEAN NOT NULL DEFAULT FALSE COMMENT '当前物理索引的完整状态回放是否完成',
    projected_count  BIGINT                                         NOT NULL DEFAULT 0 COMMENT '已成功回放候选人数量',
    `version`        INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才搜索当前状态回放进度';

CREATE TABLE `search_index_rebuild_run`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    task_id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '异步任务 ID',
    target_index         VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标搜索索引名称',
    status               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    projected_count      BIGINT                                             NOT NULL DEFAULT 0 COMMENT '预计数量',
    error_code           VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    requested_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求发起人用户 ID',
    started_at           TIMESTAMP(6) NULL COMMENT '开始处理时间',
    finished_at          TIMESTAMP(6) NULL COMMENT '完成处理时间',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`            INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_search_rebuild_task UNIQUE (task_id),
    CONSTRAINT ck_search_rebuild_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才搜索索引重建任务';

CREATE TABLE `recruitment_engagement_lock_policy`
(
    policy_key    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '策略键',
    duration_days INT          NOT NULL COMMENT '时长天数',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT ck_recruitment_lock_duration CHECK (duration_days BETWEEN 1 AND 90)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人跟进锁定策略';

CREATE TABLE `recruitment_candidate_engagement_lock`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    candidate_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '候选人 ID',
    owner_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '业务归属部门 ID',
    contact_name        VARCHAR(160)                                      NOT NULL COMMENT '联系人名称',
    status              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    acquired_at         TIMESTAMP(6)                                      NOT NULL COMMENT '获得时间',
    expires_at          TIMESTAMP(6)                                      NOT NULL COMMENT '过期时间',
    last_progress_type  VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近进度类型',
    `version`           INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_recruitment_lock_candidate UNIQUE (candidate_id),
    CONSTRAINT ck_recruitment_lock_status CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'TRANSFERRED', 'OVERRIDDEN')),
    INDEX               ix_recruitment_lock_owner (owner_user_id,status,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人跟进锁定记录';

CREATE TABLE `recruitment_candidate_engagement_lock_history`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    lock_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '锁定ID',
    candidate_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '候选人 ID',
    action_code        VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作编码',
    from_owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '变更前负责人用户 ID',
    to_owner_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '变更后负责人用户 ID',
    progress_type      VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '进度类型',
    reason             VARCHAR(500)                                      NOT NULL COMMENT '操作原因',
    actor_user_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际操作人用户 ID',
    occurred_at        TIMESTAMP(6)                                      NOT NULL COMMENT '事件发生时间',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_recruitment_lock_history FOREIGN KEY (lock_id) REFERENCES recruitment_candidate_engagement_lock (id) ON DELETE RESTRICT,
    INDEX              ix_recruitment_lock_history_candidate (candidate_id,occurred_at DESC,id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人跟进锁定历史';

CREATE TABLE `recruitment_engagement_lock_policy_history`
(
    id                     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    policy_key             VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '策略键',
    previous_duration_days INT                                               NOT NULL COMMENT '上一时长天数',
    new_duration_days      INT                                               NOT NULL COMMENT '新时长天数',
    reason                 VARCHAR(500)                                      NOT NULL COMMENT '操作原因',
    actor_user_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '实际操作人用户 ID',
    occurred_at            TIMESTAMP(6)                                      NOT NULL COMMENT '事件发生时间',
    `ext`                  TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`               VARCHAR(255) NULL COMMENT '备注',
    `create_id`            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`          VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`          TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`          VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`          TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`              INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT ck_recruitment_lock_policy_history_days CHECK (
        previous_duration_days BETWEEN 1 AND 90 AND new_duration_days BETWEEN 1 AND 90
        ),
    INDEX                  ix_recruitment_lock_policy_history (policy_key, occurred_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人跟进锁定策略历史';

CREATE TABLE `recruitment_engagement_collaboration_request`
(
    id                      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    lock_id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '锁定ID',
    candidate_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '候选人 ID',
    requester_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '申请人用户 ID',
    requester_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '申请人部门 ID',
    lock_owner_user_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '锁定负责人用户ID',
    request_reason          VARCHAR(500)                                      NOT NULL COMMENT '请求原因',
    status                  VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    active_marker           CHAR(1) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '生效记录唯一标记',
    resolved_by_user_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '解决操作人用户 ID',
    resolution_reason       VARCHAR(500) NULL COMMENT '解决原因',
    resolved_at             TIMESTAMP(6) NULL COMMENT '解决时间',
    `ext`                   TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                VARCHAR(255) NULL COMMENT '备注',
    `create_id`             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`           VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`           TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`           VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`           TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`               INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_recruitment_collaboration_active UNIQUE (lock_id, requester_user_id, active_marker),
    CONSTRAINT fk_recruitment_collaboration_lock FOREIGN KEY (lock_id) REFERENCES recruitment_candidate_engagement_lock (id) ON DELETE RESTRICT,
    CONSTRAINT ck_recruitment_collaboration_status CHECK (status IN ('REQUESTED', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_recruitment_collaboration_marker CHECK (active_marker IS NULL OR active_marker = 'Y'),
    INDEX                   ix_recruitment_collaboration_candidate (candidate_id, create_time DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人协作请求';

CREATE TABLE `recruitment_application`
(
    id                           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    candidate_id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    demand_id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '招聘需求 ID',
    jd_id                        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '职位描述（JD）ID',
    jd_version_id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '职位描述（JD）版本 ID',
    resume_version_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '简历版本 ID',
    workflow_template_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '招聘流程模板版本 ID',
    match_plan_version_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配方案版本 ID',
    recruitment_type             VARCHAR(20)                                    NOT NULL COMMENT '招聘类型',
    progressive_follow_up     BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否由 HR 逐步选择下一动作，历史流程保持原行为',
    current_stage                VARCHAR(32)                                    NOT NULL COMMENT '当前招聘阶段',
    outcome                      VARCHAR(16) NULL COMMENT '流程结果',
    owner_user_id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务归属部门 ID',
    active_slot                  TINYINT NULL DEFAULT 1 COMMENT '当前生效版本占位标记',
    terminal_at                  DATETIME(6) NULL COMMENT '进入终态的时间',
    `version`                    INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`                        TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                     VARCHAR(255) NULL COMMENT '备注',
    `create_id`                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`                VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`                TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`                VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`                TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recruitment_application_active (candidate_id, jd_id, active_slot),
    INDEX                        ix_recruitment_application_owner (owner_user_id, current_stage, update_time, id),
    INDEX                        ix_recruitment_application_jd (jd_id, jd_version_id, update_time, id),
    INDEX                        ix_recruitment_application_metric_time (create_time, id),
    CONSTRAINT ck_recruitment_application_type CHECK (recruitment_type IN ('INTERNAL', 'EXTERNAL')),
    CONSTRAINT ck_recruitment_application_stage CHECK (current_stage IN
                                                       ('PATH_SELECTION', 'IN_PROCESS', 'DECISION', 'OFFER', 'TERMINATED', 'ONBOARDED')),
    CONSTRAINT ck_recruitment_application_outcome CHECK (outcome IS NULL OR outcome IN ('PASS', 'REJECT', 'WITHDRAW')),
    CONSTRAINT fk_recruitment_application_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_application_jd FOREIGN KEY (jd_id) REFERENCES demand_jd (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_application_jd_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_application_resume FOREIGN KEY (resume_version_id) REFERENCES talent_resume_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_application_workflow FOREIGN KEY (workflow_template_version_id) REFERENCES demand_workflow_template_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_application_match_plan FOREIGN KEY (match_plan_version_id) REFERENCES matching_jd_match_plan_version (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人应聘流程';

CREATE TABLE `recruitment_application_collaborator`
(
    application_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    user_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID',
    added_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '添加操作人用户 ID',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`        INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (application_id, user_id),
    CONSTRAINT fk_recruitment_app_collaborator_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应聘流程协作人关系';

CREATE TABLE `recruitment_application_collaborator_change_record`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    before_user_ids_json JSON                                           NOT NULL COMMENT '变更前用户ID 列表（JSON）',
    after_user_ids_json  JSON                                           NOT NULL COMMENT '变更后用户ID 列表（JSON）',
    reason               VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    application_version  BIGINT                                         NOT NULL COMMENT '应聘流程版本',
    operated_by_user_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`            INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX                ix_recruitment_collaborator_change (application_id, create_time, id),
    CONSTRAINT fk_recruitment_collaborator_change_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应聘流程协作人变更记录';

CREATE TABLE `recruitment_stage_history`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    from_stage          VARCHAR(32) NULL COMMENT '变更前招聘阶段',
    to_stage            VARCHAR(32)                                    NOT NULL COMMENT '变更后招聘阶段',
    reason              VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    operated_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
    occurred_at         DATETIME(6) NOT NULL COMMENT '事件发生时间',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX               ix_recruitment_stage_history (application_id, occurred_at, id),
    CONSTRAINT fk_recruitment_stage_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应聘流程阶段变更历史';

CREATE TABLE `recruitment_application_workflow_step`
(
    id                        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    template_version_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模板版本 ID',
    step_definition_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '模板步骤定义 ID；为空表示 HR 自选动作',
    step_code                 VARCHAR(64)                                    NOT NULL COMMENT '步骤编码',
    round_no                  INT NULL COMMENT '实际面试轮次，由逐次追加计算',
    sequence_no               INT                                            NOT NULL COMMENT '顺序号',
    candidate_optional        BOOLEAN                                        NOT NULL COMMENT '候选人是否可以跳过该步骤',
    default_follow_up_channel VARCHAR(32) NULL COMMENT '默认跟进UP渠道',
    status                    VARCHAR(20)                                    NOT NULL COMMENT '当前状态',
    owner_user_id             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '业务负责人用户 ID',
    due_at                    DATETIME(6) NULL COMMENT '截止时间',
    started_at                DATETIME(6) NULL COMMENT '开始处理时间',
    completed_at              DATETIME(6) NULL COMMENT '完成时间',
    result_reason             VARCHAR(500) NULL COMMENT '结果原因',
    client_submission_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '客户推荐记录 ID',
    `version`                 INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`                     TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                  VARCHAR(255) NULL COMMENT '备注',
    `create_id`               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`             VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`             TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`             VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`             TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recruitment_application_step_sequence (application_id, sequence_no),
    INDEX                     ix_recruitment_step_work (owner_user_id, status, due_at, id),
    CONSTRAINT ck_recruitment_step_status CHECK (status IN
                                                 ('AVAILABLE', 'NOT_SELECTED', 'PENDING', 'IN_PROGRESS', 'PASSED', 'FAILED', 'WITHDRAWN', 'SKIPPED')),
    CONSTRAINT fk_recruitment_step_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应聘流程步骤实例';

CREATE TABLE `recruitment_workflow_path_change_record`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    change_type          VARCHAR(16)                                    NOT NULL COMMENT '变更类型',
    before_step_ids_json JSON                                           NOT NULL COMMENT '变更前步骤ID 列表（JSON）',
    after_step_ids_json  JSON                                           NOT NULL COMMENT '变更后步骤ID 列表（JSON）',
    reason               VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    application_version  BIGINT                                         NOT NULL COMMENT '应聘流程版本',
    operated_by_user_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`            INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX                ix_recruitment_path_change (application_id, create_time, id),
    CONSTRAINT ck_recruitment_path_change_type CHECK (change_type IN ('SELECTION', 'AMENDMENT', 'NEXT_ACTION')),
    CONSTRAINT fk_recruitment_path_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应聘流程路径变更记录';

CREATE TABLE `recruitment_workflow_follow_up_record`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    workflow_step_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '招聘流程步骤 ID',
    channel             VARCHAR(32)                                    NOT NULL COMMENT '通知或沟通渠道',
    summary             VARCHAR(1000)                                  NOT NULL COMMENT '摘要',
    next_plan           VARCHAR(500)                                   NOT NULL COMMENT '下一步方案',
    contacted_at        DATETIME(6) NOT NULL COMMENT '联系时间',
    recorded_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '记录人用户 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX               ix_recruitment_follow_up (workflow_step_id, contacted_at, id),
    CONSTRAINT ck_recruitment_follow_up_channel CHECK (channel IN
                                                       ('PHONE', 'EMAIL', 'WECHAT', 'ENTERPRISE_WECHAT', 'IN_PERSON', 'CLIENT_SYSTEM', 'OTHER')),
    CONSTRAINT fk_recruitment_follow_up_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_follow_up_step FOREIGN KEY (workflow_step_id) REFERENCES recruitment_application_workflow_step (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应聘流程跟进记录';

CREATE TABLE `recruitment_communication`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    channel             VARCHAR(32)                                    NOT NULL COMMENT '通知或沟通渠道',
    summary             VARCHAR(1000)                                  NOT NULL COMMENT '摘要',
    result              VARCHAR(32)                                    NOT NULL COMMENT '结果',
    next_plan           VARCHAR(500)                                   NOT NULL COMMENT '下一步方案',
    communicated_at     DATETIME(6) NOT NULL COMMENT '沟通时间',
    recorded_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '记录人用户 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX               ix_recruitment_communication (application_id, communicated_at, id),
    CONSTRAINT fk_recruitment_communication_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人沟通记录';

CREATE TABLE `recruitment_intention`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    candidate_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    application_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '应聘流程 ID',
    expected_salary_min DECIMAL(15, 2) NULL COMMENT '期望薪资下限',
    expected_salary_max DECIMAL(15, 2) NULL COMMENT '期望薪资上限',
    salary_period       VARCHAR(20) NULL COMMENT '薪资周期',
    availability        VARCHAR(120) NULL COMMENT '到岗时间或可用性',
    intention_locations VARCHAR(500) NULL COMMENT '意向工作地点',
    reason              VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    recorded_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '记录人用户 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX               ix_recruitment_intention_candidate (candidate_id, create_time, id),
    INDEX               ix_recruitment_intention_application (application_id, create_time, id),
    CONSTRAINT fk_recruitment_intention_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_intention_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人求职意向记录';

CREATE TABLE `recruitment_decision_record`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    record_type          VARCHAR(16)                                    NOT NULL COMMENT '记录类型',
    decision             VARCHAR(16)                                    NOT NULL COMMENT '人工决定',
    reason               VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    corrects_record_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CORRECTS记录ID',
    client_submission_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '客户推荐记录 ID',
    decided_by_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '决定操作人用户ID',
    decided_at           DATETIME(6) NOT NULL COMMENT '决定时间',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`            INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX                ix_recruitment_decision (application_id, create_time, id),
    INDEX                ix_recruitment_decision_metric_time (decided_at, id),
    CONSTRAINT ck_recruitment_decision_type CHECK (record_type IN ('DECISION', 'CORRECTION')),
    CONSTRAINT ck_recruitment_decision_value CHECK (decision IN ('PASS', 'REJECT', 'WITHDRAW')),
    CONSTRAINT fk_recruitment_decision_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_decision_correction FOREIGN KEY (corrects_record_id) REFERENCES recruitment_decision_record (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘人工决定记录';

CREATE TABLE `recruitment_ownership_history`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    action_type         VARCHAR(16)                                    NOT NULL COMMENT '操作类型',
    from_user_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '变更前用户 ID',
    to_user_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更后用户 ID',
    reason              VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    operated_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX               ix_recruitment_ownership_history (application_id, create_time, id),
    CONSTRAINT ck_recruitment_ownership_action CHECK (action_type IN ('ASSIGN', 'TRANSFER', 'HANDOVER')),
    CONSTRAINT fk_recruitment_ownership_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='应聘流程归属变更历史';

CREATE TABLE `recruitment_offer_status_record`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    record_type         VARCHAR(16)                                    NOT NULL COMMENT '记录类型',
    status              VARCHAR(20)                                    NOT NULL COMMENT '当前状态',
    termination_reason  VARCHAR(32) NULL COMMENT '终止原因',
    application_outcome VARCHAR(16) NULL COMMENT '应聘流程结果',
    reason              VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    corrects_record_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CORRECTS记录ID',
    reopens_record_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '重开记录ID',
    source              VARCHAR(32)                                    NOT NULL COMMENT '来源',
    source_reference    VARCHAR(120) NULL COMMENT '外部来源引用',
    source_status       VARCHAR(120) NULL COMMENT '外部系统原始状态',
    occurred_at         DATETIME(6) NOT NULL COMMENT '事件发生时间',
    operated_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX               ix_recruitment_offer_history (application_id, occurred_at, id),
    UNIQUE KEY uk_recruitment_offer_source (application_id, source, source_reference),
    CONSTRAINT ck_recruitment_offer_record_type CHECK (record_type IN ('STATUS', 'CORRECTION', 'REOPEN')),
    CONSTRAINT ck_recruitment_offer_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'ISSUED', 'ACCEPTED', 'TERMINATED', 'ONBOARDED')),
    CONSTRAINT fk_recruitment_offer_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_offer_correction FOREIGN KEY (corrects_record_id) REFERENCES recruitment_offer_status_record (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recruitment_offer_reopen FOREIGN KEY (reopens_record_id) REFERENCES recruitment_offer_status_record (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Offer 状态记录';

CREATE TABLE `recruitment_onboarding_event`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    onboarded_at        DATETIME(6) NOT NULL COMMENT '入职时间',
    source              VARCHAR(32)                                    NOT NULL COMMENT '来源',
    source_reference    VARCHAR(120) NULL COMMENT '外部来源引用',
    reason              VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    operated_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recruitment_onboarding_application (application_id),
    UNIQUE KEY uk_recruitment_onboarding_source (source, source_reference),
    INDEX ix_recruitment_onboarding_metric_time (onboarded_at, id),
    CONSTRAINT fk_recruitment_onboarding_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人入职事件';

CREATE TABLE `recruitment_idempotency_record`
(
    actor_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '实际操作人用户 ID',
    idempotency_key     VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等键',
    action_code         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '操作编码',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求指纹',
    target_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '目标记录 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (actor_user_id, idempotency_key),
    INDEX               ix_recruitment_idempotency_target (action_code, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘流程幂等记录';

CREATE TABLE `interview_template`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '主键 ID',
    code               VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务编码',
    name               VARCHAR(120)                                      NOT NULL COMMENT '名称',
    status             VARCHAR(16)                                       NOT NULL COMMENT '当前状态',
    current_version_no INT                                               NOT NULL COMMENT '当前业务版本号',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_interview_template_code(code),
    CONSTRAINT ck_interview_template_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试模板';

CREATE TABLE `interview_template_version`
(
    id               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    template_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模板 ID',
    version_no       INT                                            NOT NULL COMMENT '业务版本号',
    subject_template VARCHAR(300)                                   NOT NULL COMMENT '主题模板',
    body_template    VARCHAR(4000)                                  NOT NULL COMMENT '正文模板',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`        INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_interview_template_version(template_id,version_no),
    CONSTRAINT fk_interview_template_version_template FOREIGN KEY (template_id) REFERENCES interview_template (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试模板版本';

CREATE TABLE `interview_interview`
(
    id                             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    application_id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    candidate_id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    jd_id                          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '职位描述（JD）ID',
    jd_version_id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '职位描述（JD）版本 ID',
    workflow_template_version_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '招聘流程模板版本 ID',
    workflow_step_id               CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '招聘流程步骤 ID',
    step_code                      VARCHAR(64)                                    NOT NULL COMMENT '步骤编码',
    interview_type                 VARCHAR(32)                                    NOT NULL COMMENT '面试类型',
    round_no                       INT                                            NOT NULL COMMENT '轮次序号',
    client_submission_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '客户推荐记录 ID',
    status                         VARCHAR(20)                                    NOT NULL COMMENT '当前状态',
    scheduled_start_at             DATETIME(6) NOT NULL COMMENT '计划开始时间',
    scheduled_end_at               DATETIME(6) NOT NULL COMMENT '计划结束时间',
    timezone_id                    VARCHAR(64)                                    NOT NULL COMMENT '时区ID',
    meeting_mode                   VARCHAR(20)                                    NOT NULL COMMENT '面试会议方式',
    location_text                  VARCHAR(500) NULL COMMENT '地点文本',
    meeting_url                    VARCHAR(1000) NULL COMMENT '线上会议地址',
    candidate_notification_channel VARCHAR(32)                                    NOT NULL COMMENT '候选人通知渠道',
    candidate_confirmation_status  VARCHAR(20)                                    NOT NULL COMMENT '候选人CONFIRMATION状态',
    owner_user_id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务归属部门 ID',
    next_responsible_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '下一步RESPONSIBLE用户ID',
    `version`                      INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`                          TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                       VARCHAR(255) NULL COMMENT '备注',
    `create_id`                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`                  VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`                  TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`                  VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`                  TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX                          ix_interview_schedule(scheduled_start_at,status,id),
    INDEX                          ix_interview_application(application_id,workflow_step_id,status),
    INDEX                          ix_interview_owner(owner_user_id,status,scheduled_start_at),
    CONSTRAINT ck_interview_type CHECK (interview_type IN ('INTERNAL', 'EXTERNAL_CLIENT', 'EXTERNAL_CLIENT_AI')),
    CONSTRAINT ck_interview_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_interview_round CHECK (round_no >= 0),
    CONSTRAINT ck_interview_time CHECK (scheduled_end_at > scheduled_start_at),
    CONSTRAINT ck_interview_meeting_mode CHECK (meeting_mode IN ('ONLINE', 'ONSITE', 'PHONE', 'CLIENT_MANAGED')),
    CONSTRAINT ck_interview_confirmation CHECK (candidate_confirmation_status IN ('PENDING', 'CONFIRMED', 'DECLINED')),
    CONSTRAINT fk_interview_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT,
    CONSTRAINT fk_interview_workflow_step FOREIGN KEY (workflow_step_id) REFERENCES recruitment_application_workflow_step (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试安排';

CREATE TABLE `interview_participant`
(
    interview_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '面试 ID',
    user_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID',
    participant_role VARCHAR(20)                                    NOT NULL COMMENT '面试参与者角色',
    response_status  VARCHAR(20)                                    NOT NULL COMMENT '响应状态',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`        INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (interview_id, user_id),
    INDEX            ix_interview_participant_user(user_id,response_status,interview_id),
    CONSTRAINT ck_interview_participant_role CHECK (participant_role IN ('INTERVIEWER', 'OBSERVER')),
    CONSTRAINT ck_interview_participant_response CHECK (response_status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    CONSTRAINT fk_interview_participant_interview FOREIGN KEY (interview_id) REFERENCES interview_interview (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试参与人';

CREATE TABLE `interview_schedule_history`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    interview_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '面试 ID',
    action_type         VARCHAR(20)                                    NOT NULL COMMENT '操作类型',
    before_snapshot     JSON NULL COMMENT '变更前数据快照（JSON）',
    after_snapshot      JSON                                           NOT NULL COMMENT '变更后数据快照（JSON）',
    reason              VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    operated_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
    occurred_at         DATETIME(6) NOT NULL COMMENT '事件发生时间',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX               ix_interview_schedule_history(interview_id,occurred_at,id),
    CONSTRAINT ck_interview_schedule_action CHECK (action_type IN ('CREATE', 'UPDATE', 'RESCHEDULE', 'CANCEL')),
    CONSTRAINT fk_interview_schedule_history_interview FOREIGN KEY (interview_id) REFERENCES interview_interview (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试排期变更历史';

CREATE TABLE `interview_feedback`
(
    id                 CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    interview_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '面试 ID',
    author_user_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '填写人用户 ID',
    feedback_kind      VARCHAR(24)                                    NOT NULL COMMENT '反馈类型',
    status             VARCHAR(16)                                    NOT NULL COMMENT '当前状态',
    current_version_no INT                                            NOT NULL COMMENT '当前业务版本号',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_interview_feedback_author(interview_id,author_user_id,feedback_kind),
    INDEX ix_interview_feedback_page(interview_id,create_time,id),
    CONSTRAINT ck_interview_feedback_kind CHECK (feedback_kind IN ('INTERNAL', 'EXTERNAL_RECORDED')),
    CONSTRAINT ck_interview_feedback_status CHECK (status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT fk_interview_feedback_interview FOREIGN KEY (interview_id) REFERENCES interview_interview (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试反馈主记录';

CREATE TABLE `interview_feedback_version`
(
    id                       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    feedback_id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '反馈记录 ID',
    version_no               INT                                            NOT NULL COMMENT '业务版本号',
    verdict                  VARCHAR(16)                                    NOT NULL COMMENT '评估结论',
    short_comment            VARCHAR(1000) NULL COMMENT '简短COMMENT',
    external_feedback_source VARCHAR(500) NULL COMMENT '外部反馈来源',
    feedback_occurred_at     DATETIME(6) NOT NULL COMMENT '反馈发生时间',
    change_reason            VARCHAR(500)                                   NOT NULL COMMENT '变更原因',
    `ext`                    TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                 VARCHAR(255) NULL COMMENT '备注',
    `create_id`              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`            VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`            TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`            VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`            TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`                INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_interview_feedback_version(feedback_id,version_no),
    INDEX ix_interview_feedback_metric_time (feedback_occurred_at, id),
    CONSTRAINT ck_interview_feedback_verdict CHECK (verdict IN ('PASS', 'FAIL')),
    CONSTRAINT fk_interview_feedback_version_feedback FOREIGN KEY (feedback_id) REFERENCES interview_feedback (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试反馈版本';

CREATE TABLE `interview_notification_intent`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    interview_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '面试 ID',
    notification_type   VARCHAR(32)                                    NOT NULL COMMENT '通知类型',
    recipient_type      VARCHAR(20)                                    NOT NULL COMMENT '接收方类型',
    recipient_reference VARCHAR(120)                                   NOT NULL COMMENT '接收方引用',
    channel             VARCHAR(32)                                    NOT NULL COMMENT '通知或沟通渠道',
    template_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模板版本 ID',
    rendered_subject    VARCHAR(300)                                   NOT NULL COMMENT '渲染后主题',
    rendered_body       VARCHAR(4000)                                  NOT NULL COMMENT '渲染后正文',
    status              VARCHAR(24)                                    NOT NULL COMMENT '当前状态',
    external_message_id VARCHAR(200) NULL COMMENT '外部消息 ID',
    last_error_code     VARCHAR(80) NULL COMMENT '最近错误编码',
    last_error_detail   VARCHAR(500) NULL COMMENT '最近错误详情',
    retry_count         INT                                            NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX               ix_interview_notification_status(status,update_time,id),
    INDEX ix_interview_notification_page(interview_id,create_time,id),
    CONSTRAINT ck_interview_notification_status CHECK (status IN ('PENDING_MANUAL', 'SENT_MANUAL', 'SENDING_EXTERNAL', 'SENT_EXTERNAL', 'FAILED_EXTERNAL')),
    CONSTRAINT fk_interview_notification_interview FOREIGN KEY (interview_id) REFERENCES interview_interview (id) ON DELETE RESTRICT,
    CONSTRAINT fk_interview_notification_template FOREIGN KEY (template_version_id) REFERENCES interview_template_version (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试通知意图';

CREATE TABLE `interview_notification_attempt`
(
    id                     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主键 ID',
    notification_intent_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '通知意图 ID',
    attempt_type           VARCHAR(20)                                    NOT NULL COMMENT '尝试类型',
    channel                VARCHAR(32)                                    NOT NULL COMMENT '通知或沟通渠道',
    status                 VARCHAR(20)                                    NOT NULL COMMENT '当前状态',
    external_message_id    VARCHAR(200) NULL COMMENT '外部消息 ID',
    error_code             VARCHAR(80) NULL COMMENT '错误编码',
    error_detail           VARCHAR(500) NULL COMMENT '错误详情',
    operated_by_user_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
    occurred_at            DATETIME(6) NOT NULL COMMENT '事件发生时间',
    `ext`                  TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`               VARCHAR(255) NULL COMMENT '备注',
    `create_id`            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`          VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`          TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`          VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`          TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`              INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (id),
    INDEX                  ix_interview_notification_attempt(notification_intent_id,occurred_at,id),
    CONSTRAINT ck_interview_notification_attempt_type CHECK (attempt_type IN ('MANUAL_SEND', 'EXTERNAL_RETRY')),
    CONSTRAINT ck_interview_notification_attempt_status CHECK (status IN ('SENT', 'FAILED')),
    CONSTRAINT fk_interview_notification_attempt_intent FOREIGN KEY (notification_intent_id) REFERENCES interview_notification_intent (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试通知发送尝试';

CREATE TABLE `interview_idempotency_record`
(
    actor_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '实际操作人用户 ID',
    idempotency_key     VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等键',
    action_code         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '操作编码',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求指纹',
    target_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '目标记录 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (actor_user_id, idempotency_key),
    INDEX               ix_interview_idempotency_target(action_code,target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='面试业务幂等记录';

CREATE TABLE `risk_company_blacklist_record`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    candidate_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    record_type         VARCHAR(20)                                    NOT NULL COMMENT '记录类型',
    status              VARCHAR(20)                                    NOT NULL COMMENT '当前状态',
    reason_code         VARCHAR(60)                                    NOT NULL COMMENT '原因编码',
    reason_detail       VARCHAR(1000)                                  NOT NULL COMMENT '原因详情',
    source_channel      VARCHAR(40)                                    NOT NULL COMMENT '来源渠道',
    evidence_reference  VARCHAR(1000)                                  NOT NULL COMMENT '证据引用',
    corrects_record_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CORRECTS记录ID',
    recorded_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '记录人用户 ID',
    occurred_at         TIMESTAMP(6)                                   NOT NULL COMMENT '事件发生时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT fk_blacklist_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id),
    CONSTRAINT fk_blacklist_corrects FOREIGN KEY (corrects_record_id) REFERENCES risk_company_blacklist_record (id),
    INDEX               ix_blacklist_candidate_time(candidate_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司黑名单记录';

CREATE TABLE `risk_company_blacklist_current`
(
    candidate_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '候选人 ID',
    record_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务记录 ID',
    active        TINYINT(1) NOT NULL COMMENT '是否处于启用状态',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_blacklist_current_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id),
    CONSTRAINT fk_blacklist_current_record FOREIGN KEY (record_id) REFERENCES risk_company_blacklist_record (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公司黑名单当前投影';

CREATE TABLE `candidate_education_verification_record`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    candidate_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    education_fact_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '教育经历事实ID',
    application_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    record_type         VARCHAR(32)                                    NOT NULL COMMENT '记录类型',
    verification_status VARCHAR(32)                                    NOT NULL COMMENT '核验状态',
    study_mode          VARCHAR(32)                                    NOT NULL COMMENT '学习形式',
    source_channel      VARCHAR(40)                                    NOT NULL COMMENT '来源渠道',
    evidence_reference  VARCHAR(1000) NULL COMMENT '证据引用',
    note                VARCHAR(1000) NULL COMMENT '备注说明',
    corrects_record_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CORRECTS记录ID',
    recorded_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '记录人用户 ID',
    occurred_at         TIMESTAMP(6)                                   NOT NULL COMMENT '事件发生时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT fk_education_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id),
    CONSTRAINT fk_education_corrects FOREIGN KEY (corrects_record_id) REFERENCES candidate_education_verification_record (id),
    INDEX               ix_education_candidate(candidate_id,create_time,id),
    INDEX               ix_education_application(application_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人教育经历核验记录';

CREATE TABLE `submission_client_submission`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    application_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应聘流程 ID',
    candidate_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    demand_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '招聘需求 ID',
    client_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户 ID',
    project_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '项目 ID',
    position_id          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '职位 ID',
    jd_id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '职位描述（JD）ID',
    jd_version_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '职位描述（JD）版本 ID',
    resume_version_id    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '简历版本 ID',
    delivery_status      VARCHAR(20)                                    NOT NULL COMMENT '客户推荐交付状态',
    process_status       VARCHAR(24)                                    NOT NULL COMMENT '处理状态',
    sent_at              TIMESTAMP(6) NULL COMMENT '发送时间',
    withdrawn_at         TIMESTAMP(6) NULL COMMENT '撤回时间',
    withdrawn_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '撤回操作人用户 ID',
    withdrawal_reason    VARCHAR(1000) NULL COMMENT '撤回原因',
    owner_user_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务负责人用户 ID',
    owner_department_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务归属部门 ID',
    `version`            INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT fk_submission_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id),
    CONSTRAINT fk_submission_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id),
    CONSTRAINT fk_submission_client FOREIGN KEY (client_id) REFERENCES demand_client (id),
    CONSTRAINT fk_submission_project FOREIGN KEY (project_id) REFERENCES demand_client_project (id),
    CONSTRAINT fk_submission_position FOREIGN KEY (position_id) REFERENCES demand_client_position (id),
    CONSTRAINT fk_submission_jd FOREIGN KEY (jd_id) REFERENCES demand_jd (id),
    CONSTRAINT fk_submission_jd_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id),
    CONSTRAINT fk_submission_resume_version FOREIGN KEY (resume_version_id) REFERENCES talent_resume_version (id),
    INDEX                ix_submission_candidate_client(candidate_id,client_id,create_time,id),
    INDEX                ix_submission_application(application_id,create_time,id),
    INDEX                ix_submission_filter(jd_id,jd_version_id,process_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人客户推荐记录';

CREATE TABLE `submission_send_snapshot`
(
    id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    submission_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户推荐记录 ID',
    resume_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '简历版本 ID',
    recipient         VARCHAR(500)                                   NOT NULL COMMENT '接收方地址',
    channel           VARCHAR(40)                                    NOT NULL COMMENT '通知或沟通渠道',
    file_name         VARCHAR(255)                                   NOT NULL COMMENT '文件名',
    content_sha256    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '内容 SHA-256 摘要',
    sent_by_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发送操作人用户 ID',
    sent_at           TIMESTAMP(6)                                   NOT NULL COMMENT '发送时间',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_send_snapshot_submission FOREIGN KEY (submission_id) REFERENCES submission_client_submission (id),
    UNIQUE KEY uk_send_snapshot_submission(submission_id),
    INDEX ix_submission_send_metric_time (sent_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户推荐发送快照';

CREATE TABLE `submission_feedback`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    submission_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户推荐记录 ID',
    result              VARCHAR(24)                                    NOT NULL COMMENT '结果',
    reason_category     VARCHAR(60)                                    NOT NULL COMMENT '原因分类',
    next_action         VARCHAR(500)                                   NOT NULL COMMENT '下一步操作',
    note                VARCHAR(1000) NULL COMMENT '备注说明',
    source_channel      VARCHAR(40)                                    NOT NULL COMMENT '来源渠道',
    source_occurred_at  TIMESTAMP(6)                                   NOT NULL COMMENT '来源事件发生时间',
    recorded_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '记录人用户 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_submission_feedback FOREIGN KEY (submission_id) REFERENCES submission_client_submission (id),
    INDEX               ix_submission_feedback(submission_id,source_occurred_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户推荐反馈';

CREATE TABLE `submission_client_restriction`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    client_id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户 ID',
    candidate_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    source_submission_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源客户推荐ID',
    record_type          VARCHAR(20)                                    NOT NULL COMMENT '记录类型',
    status               VARCHAR(20)                                    NOT NULL COMMENT '当前状态',
    restriction_intent   VARCHAR(40)                                    NOT NULL COMMENT '客户禁推意图',
    expires_at           TIMESTAMP(6) NULL COMMENT '过期时间',
    reason_detail        VARCHAR(1000)                                  NOT NULL COMMENT '原因详情',
    source_channel       VARCHAR(40)                                    NOT NULL COMMENT '来源渠道',
    source_occurred_at   TIMESTAMP(6)                                   NOT NULL COMMENT '来源事件发生时间',
    corrects_record_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CORRECTS记录ID',
    recorded_by_user_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '记录人用户 ID',
    `version`            INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT fk_restriction_client FOREIGN KEY (client_id) REFERENCES demand_client (id),
    CONSTRAINT fk_restriction_candidate FOREIGN KEY (candidate_id) REFERENCES talent_candidate (id),
    CONSTRAINT fk_restriction_submission FOREIGN KEY (source_submission_id) REFERENCES submission_client_submission (id),
    CONSTRAINT fk_restriction_corrects FOREIGN KEY (corrects_record_id) REFERENCES submission_client_restriction (id),
    INDEX                ix_restriction_candidate_client(candidate_id,client_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人客户禁推记录';

CREATE TABLE `submission_client_restriction_current`
(
    client_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户 ID',
    candidate_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '候选人 ID',
    record_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务记录 ID',
    active        TINYINT(1) NOT NULL COMMENT '是否处于启用状态',
    expires_at    TIMESTAMP(6) NULL COMMENT '过期时间',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (client_id, candidate_id),
    CONSTRAINT fk_restriction_current_record FOREIGN KEY (record_id) REFERENCES submission_client_restriction (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人客户禁推当前投影';

CREATE TABLE `submission_risk_acknowledgement`
(
    id                      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    submission_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户推荐记录 ID',
    risk_code               VARCHAR(60)                                    NOT NULL COMMENT '风险编码',
    reason                  VARCHAR(1000)                                  NOT NULL COMMENT '操作原因',
    acknowledged_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '风险确认人用户 ID',
    `ext`                   TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                VARCHAR(255) NULL COMMENT '备注',
    `create_id`             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`           VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`           TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`           VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`           TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`               INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT fk_risk_ack_submission FOREIGN KEY (submission_id) REFERENCES submission_client_submission (id),
    INDEX                   ix_risk_ack_submission(submission_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户推荐风险确认记录';

CREATE TABLE `submission_idempotency_record`
(
    actor_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际操作人用户 ID',
    idempotency_key     VARCHAR(128)                                   NOT NULL COMMENT '幂等键',
    action_code         VARCHAR(64)                                    NOT NULL COMMENT '操作编码',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求指纹',
    target_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标记录 ID',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    PRIMARY KEY (actor_user_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户推荐幂等记录';

CREATE TABLE `reporting_task_item`
(
    id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    task_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '异步任务 ID',
    item_type         VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '任务条目类型',
    item_reference    VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任务条目引用',
    display_name      VARCHAR(255)                                       NOT NULL COMMENT '用户展示名称',
    status            VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    error_code        VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '错误编码',
    safe_error_detail VARCHAR(500) NULL COMMENT '可安全展示的错误详情',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_task_item(task_id,item_type,item_reference),
    INDEX             ix_reporting_task_item_status(task_id,status,id),
    CONSTRAINT fk_reporting_task_item_task FOREIGN KEY (task_id) REFERENCES reporting_async_task (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reporting_task_item_status CHECK (status IN ('WAITING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'IGNORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表异步任务条目';

CREATE TABLE `reporting_user_overdue_threshold_version`
(
    id                            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    user_id                       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID',
    version_no                    INT                                            NOT NULL COMMENT '业务版本号',
    first_processing_hours        INT                                            NOT NULL COMMENT '首次处理小时数',
    communication_follow_up_hours INT                                            NOT NULL COMMENT '沟通后跟进时限（小时）',
    interview_feedback_hours      INT                                            NOT NULL COMMENT '面试反馈小时数',
    client_feedback_hours         INT                                            NOT NULL COMMENT '客户反馈小时数',
    offer_processing_hours        INT                                            NOT NULL COMMENT 'Offer 处理时限（小时）',
    reason                        VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    effective_from                TIMESTAMP(6)                                   NOT NULL COMMENT '生效起始时间',
    `ext`                         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                      VARCHAR(255) NULL COMMENT '备注',
    `create_id`                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`                 VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`                 TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`                 VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`                 TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`                     INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_overdue_version(user_id,version_no),
    INDEX                         ix_reporting_overdue_effective(user_id,effective_from DESC,id),
    CONSTRAINT ck_reporting_overdue_first CHECK (first_processing_hours BETWEEN 1 AND 720),
    CONSTRAINT ck_reporting_overdue_communication CHECK (communication_follow_up_hours BETWEEN 1 AND 720),
    CONSTRAINT ck_reporting_overdue_interview CHECK (interview_feedback_hours BETWEEN 1 AND 720),
    CONSTRAINT ck_reporting_overdue_client CHECK (client_feedback_hours BETWEEN 1 AND 720),
    CONSTRAINT ck_reporting_overdue_offer CHECK (offer_processing_hours BETWEEN 1 AND 720)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户逾期阈值版本';

CREATE TABLE `reporting_metric_definition`
(
    code               VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '业务编码',
    name               VARCHAR(120)                                      NOT NULL COMMENT '名称',
    category           VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分类',
    current_version_no INT                                               NOT NULL COMMENT '当前业务版本号',
    status             VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前状态',
    `ext`              TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`      VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`      VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`      TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`          INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT ck_reporting_metric_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表指标定义';

CREATE TABLE `reporting_metric_version`
(
    id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    metric_code     VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '指标编码',
    version_no      INT                                               NOT NULL COMMENT '业务版本号',
    definition_text VARCHAR(1000)                                     NOT NULL COMMENT '定义文本',
    event_type      VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型',
    effective_from  TIMESTAMP(6)                                      NOT NULL COMMENT '生效起始时间',
    `ext`           TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`        VARCHAR(255) NULL COMMENT '备注',
    `create_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`   VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`   VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`       INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_metric_version(metric_code,version_no),
    CONSTRAINT fk_reporting_metric_version_definition FOREIGN KEY (metric_code) REFERENCES reporting_metric_definition (code) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表指标版本';

CREATE TABLE `reporting_metric_event`
(
    id                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    event_type        VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型',
    source_type       VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据来源类型',
    source_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '来源记录 ID',
    actor_user_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '实际操作人用户 ID',
    owner_user_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '业务负责人用户 ID',
    department_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '部门 ID',
    jd_id             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '职位描述（JD）ID',
    jd_version_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '职位描述（JD）版本 ID',
    client_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '客户 ID',
    source_channel_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '来源渠道 ID',
    value_decimal     DECIMAL(18, 4)                                    NOT NULL DEFAULT 1 COMMENT '值小数',
    occurred_at       TIMESTAMP(6)                                      NOT NULL COMMENT '事件发生时间',
    captured_at       TIMESTAMP(6)                                      NOT NULL COMMENT '采集时间',
    `ext`             TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`          VARCHAR(255) NULL COMMENT '备注',
    `create_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`     VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`     VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`     TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`         INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_metric_source(event_type,source_type,source_id),
    INDEX             ix_reporting_metric_slice(event_type,occurred_at,owner_user_id,department_id,jd_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表指标事实事件';

CREATE TABLE `reporting_work_target`
(
    id                       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    target_user_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标用户 ID',
    target_date              DATE                                           NOT NULL COMMENT '目标日期',
    resume_processing_target INT                                            NOT NULL COMMENT '简历处理目标',
    recommendable_target     INT                                            NOT NULL COMMENT '可推荐目标目标',
    reason                   VARCHAR(500)                                   NOT NULL COMMENT '操作原因',
    set_by_user_id           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '设置操作人用户ID',
    `ext`                    TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                 VARCHAR(255) NULL COMMENT '备注',
    `create_id`              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`            VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`            TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`            VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`            TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`                INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_work_target(target_user_id,target_date),
    CONSTRAINT ck_reporting_target_resume CHECK (resume_processing_target BETWEEN 0 AND 10000),
    CONSTRAINT ck_reporting_target_recommendable CHECK (recommendable_target BETWEEN 0 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='招聘工作目标';

CREATE TABLE `reporting_export_job`
(
    id                         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    task_id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '异步任务 ID',
    export_type                VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '导出类型',
    resource_type              VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '资源类型',
    status                     VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '当前状态',
    query_snapshot             JSON                                               NOT NULL COMMENT '查询条件快照（JSON）',
    fields_snapshot            JSON                                               NOT NULL COMMENT '字段值快照（JSON）',
    scope_snapshot             JSON                                               NOT NULL COMMENT '权限范围快照（JSON）',
    requested_by_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求发起人用户 ID',
    requested_by_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求发起部门 ID',
    total_rows                 BIGINT                                             NOT NULL DEFAULT 0 COMMENT '总数据行数',
    file_count                 INT                                                NOT NULL DEFAULT 0 COMMENT '文件总数',
    expires_at                 TIMESTAMP(6) NULL COMMENT '过期时间',
    failure_code               VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '失败编码',
    safe_failure_detail        VARCHAR(500) NULL COMMENT '可安全展示的失败详情',
    idempotency_key            VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '幂等键',
    request_fingerprint        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '请求指纹',
    `ext`                      TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`                   VARCHAR(255) NULL COMMENT '备注',
    `create_id`                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`              VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`              TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`                CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`              VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`              TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`                  INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_export_idempotency(requested_by_user_id,idempotency_key),
    INDEX                      ix_reporting_export_active(requested_by_user_id,status,create_time,id),
    CONSTRAINT fk_reporting_export_task FOREIGN KEY (task_id) REFERENCES reporting_async_task (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reporting_export_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_reporting_export_type CHECK (export_type IN ('TABLE_ONLY', 'TABLE_WITH_RESUMES', 'TABLE_WITH_LINKS')),
    INDEX                      ix_reporting_export_history (requested_by_user_id, create_time DESC, id DESC),
    INDEX                      ix_reporting_export_expiry (status, expires_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表导出任务';

CREATE TABLE `reporting_export_object`
(
    id            CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '与导出文件共用的唯一 ID',
    export_job_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属导出任务 ID',
    object_key    VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上传前预留且不复用的对象键',
    cleanup_after TIMESTAMP(6) NOT NULL COMMENT '最早清理时间，删除失败后延后重试',
    `ext`         TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `create_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`     INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_export_object_key(object_key),
    INDEX ix_reporting_export_object_cleanup(cleanup_after,id),
    CONSTRAINT fk_reporting_export_object_job FOREIGN KEY (export_job_id) REFERENCES reporting_export_job (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='导出对象生命周期登记';

CREATE TABLE `reporting_export_file`
(
    id             CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    export_job_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '导出任务 ID',
    part_no        INT                                                NOT NULL COMMENT '分片序号',
    row_count      INT                                                NOT NULL COMMENT '数据行数',
    file_name      VARCHAR(255)                                       NOT NULL COMMENT '文件名',
    media_type     VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件媒体类型',
    object_key     VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对象存储键',
    content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '内容 SHA-256 摘要',
    size_bytes     BIGINT                                             NOT NULL COMMENT '文件大小（字节）',
    expires_at     TIMESTAMP(6)                                       NOT NULL COMMENT '过期时间',
    `ext`          TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`       VARCHAR(255) NULL COMMENT '备注',
    `create_id`    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`  VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`  TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`  VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`  TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`      INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_export_file_part(export_job_id,part_no),
    CONSTRAINT fk_reporting_export_file_job FOREIGN KEY (export_job_id) REFERENCES reporting_export_job (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reporting_export_file_rows CHECK (row_count BETWEEN 0 AND 100000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表导出文件';

CREATE TABLE `reporting_export_download_audit`
(
    id                    CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    export_job_id         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '导出任务 ID',
    export_file_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '导出文件 ID',
    downloaded_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '下载操作人用户 ID',
    occurred_at           TIMESTAMP(6)                                   NOT NULL COMMENT '事件发生时间',
    `ext`                 TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`              VARCHAR(255) NULL COMMENT '备注',
    `create_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`         VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`         TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`           CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`         VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`         TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`             INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    INDEX                 ix_reporting_export_download(export_job_id,occurred_at,id),
    CONSTRAINT fk_reporting_download_job FOREIGN KEY (export_job_id) REFERENCES reporting_export_job (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reporting_download_file FOREIGN KEY (export_file_id) REFERENCES reporting_export_file (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表导出下载审计';

CREATE TABLE `reporting_metric_capture_checkpoint`
(
    `checkpoint_name` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '来源与通道组成的检查点名称',
    `source_code` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '指标来源编码',
    `lane` VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'INCREMENTAL 或 RECONCILIATION',
    `last_source_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上次已扫描来源主键',
    `cycle_upper_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '本周期固定上界，空值表示周期已完成',
    `cycle_started_at` TIMESTAMP(6) NULL COMMENT '进行中周期起点',
    `last_completed_at` TIMESTAMP(6) NULL COMMENT '最近完整周期起点，首次未完成时为空',
    `version` INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext` TEXT NULL COMMENT 'JSON 扩展信息',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表指标独立增量及补偿水位';

CREATE TABLE `reporting_audit_capture_checkpoint`
(
    checkpoint_name CHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '来源与采集通道组成的稳定检查点名称',
    source_code     VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审计来源编码',
    lane            VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '采集通道',
    last_source_id  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin    NOT NULL COMMENT '上次完成扫描的来源记录 ID',
    scanned_count   BIGINT                                            NOT NULL DEFAULT 0 COMMENT '累计扫描记录数',
    `ext`           TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`        VARCHAR(255) NULL COMMENT '备注',
    `create_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`   VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`   VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`   TIMESTAMP(6)                                      NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`       INT                                               NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_audit_checkpoint_lane(source_code,lane),
    CONSTRAINT ck_reporting_audit_checkpoint_lane CHECK (lane IN ('INCREMENTAL','RECONCILIATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计投影增量与校对采集检查点';

CREATE TABLE `reporting_audit_event`
(
    id              CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '主键 ID',
    event_type      VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '事件类型',
    actor_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '实际操作人用户 ID',
    target_type     VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '目标类型',
    target_id       VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标记录 ID',
    owner_user_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '业务负责人用户 ID',
    department_id   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '部门 ID',
    reason          VARCHAR(500) NULL COMMENT '操作原因',
    before_snapshot JSON NULL COMMENT '变更前数据快照（JSON）',
    after_snapshot  JSON NULL COMMENT '变更后数据快照（JSON）',
    source_module   VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin  NOT NULL COMMENT '来源业务模块',
    source_event_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin     NOT NULL COMMENT '来源事件 ID',
    occurred_at     TIMESTAMP(6)                                       NOT NULL COMMENT '事件发生时间',
    captured_at     TIMESTAMP(6)                                       NOT NULL COMMENT '采集时间',
    `ext`           TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`        VARCHAR(255) NULL COMMENT '备注',
    `create_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`   VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`   TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`   VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`   TIMESTAMP(6)                                       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`       INT                                                NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_audit_source(source_module,source_event_id),
    INDEX           ix_reporting_audit_search(occurred_at DESC,event_type,actor_user_id,target_type,target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表业务审计事件';

-- Standalone indexes
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

-- Required seed data
INSERT INTO identity_role (id, code, name, system_role, status, version, create_time)
VALUES ('00000000000000000000000001', 'SUPER_ADMIN', '超级管理员', TRUE, 'ACTIVE', 0, UTC_TIMESTAMP(6)),
       ('00000000000000000000000002', 'RECRUITER', '招聘专员', TRUE, 'ACTIVE', 0, UTC_TIMESTAMP(6)),
       ('00000000000000000000000003', 'RECRUITMENT_MANAGER', '招聘负责人', TRUE, 'ACTIVE', 0, UTC_TIMESTAMP(6)),
       ('00000000000000000000000004', 'INTERVIEWER', '面试官', TRUE, 'ACTIVE', 0, UTC_TIMESTAMP(6));

INSERT INTO identity_permission (code, name, category, create_time)
VALUES ('identity.user.register', '注册招聘账号', 'IDENTITY', UTC_TIMESTAMP(6)),
       ('identity.user.credential-reset', '重置账号凭据', 'IDENTITY', UTC_TIMESTAMP(6)),
       ('identity.user.disable', '停用招聘账号', 'IDENTITY', UTC_TIMESTAMP(6)),
       ('identity.organization.manage', '维护组织结构', 'IDENTITY', UTC_TIMESTAMP(6)),
       ('identity.role.assign', '分配普通角色', 'IDENTITY', UTC_TIMESTAMP(6)),
       ('identity.super-admin.manage', '授予或撤销超级管理员', 'IDENTITY', UTC_TIMESTAMP(6)),
       ('identity.handover.preview', '预览离职交接', 'IDENTITY', UTC_TIMESTAMP(6)),
       ('identity.handover.execute', '执行离职交接', 'IDENTITY', UTC_TIMESTAMP(6)),
       ('identity.handover.resolve', '处置不可转交事项', 'IDENTITY', UTC_TIMESTAMP(6)),
       ('demand.read', '查看招聘需求', 'DEMAND', UTC_TIMESTAMP(6)),
       ('demand.manage', '维护招聘需求', 'DEMAND', UTC_TIMESTAMP(6)),
       ('talent.read', '查看共享人才库', 'TALENT', UTC_TIMESTAMP(6)),
       ('talent.manage', '维护共享人才库', 'TALENT', UTC_TIMESTAMP(6)),
       ('matching.run', '运行智能匹配', 'MATCHING', UTC_TIMESTAMP(6)),
       ('recruitment.manage', '推进招聘流程', 'RECRUITMENT', UTC_TIMESTAMP(6)),
       ('interview.read', '查看本人面试', 'INTERVIEW', UTC_TIMESTAMP(6)),
       ('interview.evaluate', '提交面试评价', 'INTERVIEW', UTC_TIMESTAMP(6));

INSERT INTO identity_role_permission (role_id, permission_code)
VALUES ('00000000000000000000000002', 'demand.read'),
       ('00000000000000000000000002', 'demand.manage'),
       ('00000000000000000000000002', 'talent.read'),
       ('00000000000000000000000002', 'talent.manage'),
       ('00000000000000000000000002', 'matching.run'),
       ('00000000000000000000000002', 'recruitment.manage'),
       ('00000000000000000000000003', 'identity.user.register'),
       ('00000000000000000000000003', 'identity.handover.preview'),
       ('00000000000000000000000003', 'demand.read'),
       ('00000000000000000000000003', 'demand.manage'),
       ('00000000000000000000000003', 'talent.read'),
       ('00000000000000000000000003', 'talent.manage'),
       ('00000000000000000000000003', 'matching.run'),
       ('00000000000000000000000003', 'recruitment.manage'),
       ('00000000000000000000000003', 'interview.read'),
       ('00000000000000000000000004', 'interview.read'),
       ('00000000000000000000000004', 'interview.evaluate');

INSERT INTO identity_permission(code, name, category, create_time)
VALUES ('talent.download', '下载原始简历', 'TALENT', UTC_TIMESTAMP(6));

INSERT INTO identity_permission(code, name, category, create_time)
VALUES ('talent.fact.correct', '纠正共享简历事实', 'TALENT', UTC_TIMESTAMP(6));

INSERT INTO recruitment_engagement_lock_policy(policy_key, duration_days, version, update_id, update_time)
VALUES ('DEFAULT', 7, 0, 'SYSTEM', UTC_TIMESTAMP(6));

INSERT INTO identity_permission(code, name, category, create_time)
VALUES ('interview.schedule', '安排、改期和取消面试', 'INTERVIEW', UTC_TIMESTAMP(6)),
       ('interview.feedback.submit-own', '提交本人面评', 'INTERVIEW', UTC_TIMESTAMP(6)),
       ('interview.external-feedback.record', '人工代录甲方外部面试反馈', 'INTERVIEW', UTC_TIMESTAMP(6)),
       ('interview.feedback.read-authorized', '查看授权面评', 'INTERVIEW', UTC_TIMESTAMP(6));

INSERT INTO identity_role_permission(role_id, permission_code)
VALUES ('00000000000000000000000002', 'interview.schedule'),
       ('00000000000000000000000002', 'interview.external-feedback.record'),
       ('00000000000000000000000002', 'interview.feedback.read-authorized'),
       ('00000000000000000000000003', 'interview.schedule'),
       ('00000000000000000000000003', 'interview.external-feedback.record'),
       ('00000000000000000000000003', 'interview.feedback.read-authorized'),
       ('00000000000000000000000004', 'interview.feedback.submit-own'),
       ('00000000000000000000000004', 'interview.feedback.read-authorized');

INSERT INTO interview_template(id, code, name, status, current_version_no, version, create_time, update_time)
VALUES ('00000000000000000000000101', 'INTERVIEW_INVITATION', '面试邀请', 'ACTIVE', 1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('00000000000000000000000102', 'INTERVIEW_RESCHEDULE', '面试改期', 'ACTIVE', 1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('00000000000000000000000103', 'INTERVIEW_CANCEL', '面试取消', 'ACTIVE', 1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('00000000000000000000000104', 'FEEDBACK_REMINDER', '面评催办', 'ACTIVE', 1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO interview_template_version(id, template_id, version_no, subject_template, body_template, create_id, create_time)
VALUES ('00000000000000000000000201', '00000000000000000000000101', 1, '面试邀请｜{{stepCode}}',
        'Application {{applicationId}} 的面试安排在 {{startAt}}（{{timezone}}），方式：{{meetingMode}}，地点/链接：{{location}}。', 'SYSTEM',
        UTC_TIMESTAMP(6)),
       ('00000000000000000000000202', '00000000000000000000000102', 1, '面试改期｜{{stepCode}}',
        'Application {{applicationId}} 的面试已改至 {{startAt}}（{{timezone}}），方式：{{meetingMode}}，地点/链接：{{location}}。', 'SYSTEM',
        UTC_TIMESTAMP(6)),
       ('00000000000000000000000203', '00000000000000000000000103', 1, '面试取消｜{{stepCode}}',
        'Application {{applicationId}} 的面试已取消，原因请查看系统内记录。', 'SYSTEM', UTC_TIMESTAMP(6)),
       ('00000000000000000000000204', '00000000000000000000000104', 1, '面评待提交｜{{stepCode}}',
        'Application {{applicationId}} 的面试已结束，请本人提交通过/不通过及可选一句话意见。', 'SYSTEM', UTC_TIMESTAMP(6));

INSERT
IGNORE INTO identity_permission(code,name,category,create_time) VALUES
 ('risk.read','查看风险与限制','RISK',UTC_TIMESTAMP(6)),
 ('risk.manage','管理公司黑名单','RISK',UTC_TIMESTAMP(6)),
 ('education.verify','记录学历核验','RISK',UTC_TIMESTAMP(6));

INSERT
IGNORE INTO identity_role_permission(role_id,permission_code)
SELECT r.id, p.code
FROM identity_role r
         JOIN identity_permission p
WHERE r.code IN ('RECRUITER', 'RECRUITMENT_MANAGER')
  AND p.code IN ('risk.read', 'risk.manage', 'education.verify');

INSERT
IGNORE INTO identity_permission(code,name,category,create_time) VALUES
 ('submission.read','查看客户推荐','SUBMISSION',UTC_TIMESTAMP(6)),
 ('submission.manage','管理客户推荐','SUBMISSION',UTC_TIMESTAMP(6));

INSERT
IGNORE INTO identity_role_permission(role_id,permission_code)
SELECT r.id, p.code
FROM identity_role r
         JOIN identity_permission p
WHERE r.code IN ('RECRUITER', 'RECRUITMENT_MANAGER')
  AND p.code IN ('submission.read', 'submission.manage');

INSERT
IGNORE INTO identity_permission(code,name,category,create_time) VALUES
 ('reporting.read-own','查看本人任务与报表','REPORTING',UTC_TIMESTAMP(6)),
 ('reporting.read-managed','查看管理范围任务与报表','REPORTING',UTC_TIMESTAMP(6)),
 ('reporting.overdue-threshold.manage-own','维护个人超时阈值','REPORTING',UTC_TIMESTAMP(6)),
 ('audit.read-authorized','查看授权范围审计','AUDIT',UTC_TIMESTAMP(6));

INSERT
IGNORE INTO identity_role_permission(role_id,permission_code)
SELECT r.id, p.code
FROM identity_role r
         JOIN identity_permission p
WHERE (r.code = 'RECRUITER' AND p.code IN ('reporting.read-own', 'reporting.overdue-threshold.manage-own'))
   OR (r.code = 'RECRUITMENT_MANAGER' AND
       p.code IN ('reporting.read-own', 'reporting.read-managed', 'reporting.overdue-threshold.manage-own', 'audit.read-authorized'));

INSERT INTO reporting_metric_definition(code, name, category, current_version_no, status, create_time)
VALUES ('RESUME_INGESTED', '简历入库量', 'FUNNEL', 1, 'ACTIVE', UTC_TIMESTAMP(6)),
       ('RESUME_PARSED', '简历解析完成量', 'AI_EFFECT', 1, 'ACTIVE', UTC_TIMESTAMP(6)),
       ('HUMAN_SCREENED', '人工完成筛选数', 'WORKLOAD', 1, 'ACTIVE', UTC_TIMESTAMP(6)),
       ('APPLICATION_CREATED', '招聘活动创建数', 'FUNNEL', 1, 'ACTIVE', UTC_TIMESTAMP(6)),
       ('SUBMISSION_SENT', '实际推送数', 'FUNNEL', 1, 'ACTIVE', UTC_TIMESTAMP(6)),
       ('INTERVIEW_PASSED', '面试通过数', 'FUNNEL', 1, 'ACTIVE', UTC_TIMESTAMP(6)),
       ('ONBOARDED', '到岗人数', 'FUNNEL', 1, 'ACTIVE', UTC_TIMESTAMP(6));

INSERT INTO reporting_metric_version(id, metric_code, version_no, definition_text, event_type, effective_from, create_time)
VALUES ('00000000000000000000000441', 'RESUME_INGESTED', 1, '以不可变 ResumeVersion 创建事件计数。', 'RESUME_INGESTED', '2026-01-01 00:00:00.000000',
        UTC_TIMESTAMP(6)),
       ('00000000000000000000000442', 'RESUME_PARSED', 1, '以有效解析快照创建事件计数。', 'RESUME_PARSED', '2026-01-01 00:00:00.000000',
        UTC_TIMESTAMP(6)),
       ('00000000000000000000000443', 'HUMAN_SCREENED', 1, '只以 HR 提交的人工决定计数，系统自动匹配不计入。', 'HUMAN_SCREENED',
        '2026-01-01 00:00:00.000000', UTC_TIMESTAMP(6)),
       ('00000000000000000000000444', 'APPLICATION_CREATED', 1, '以 Application 创建事件计数。', 'APPLICATION_CREATED', '2026-01-01 00:00:00.000000',
        UTC_TIMESTAMP(6)),
       ('00000000000000000000000445', 'SUBMISSION_SENT', 1, '以不可变客户推荐发送快照计数。', 'SUBMISSION_SENT', '2026-01-01 00:00:00.000000',
        UTC_TIMESTAMP(6)),
       ('00000000000000000000000446', 'INTERVIEW_PASSED', 1, '以最新已提交面评版本的 PASS 事实计数，内部、甲方 AI 与其他甲方面试分型保留。',
        'INTERVIEW_PASSED', '2026-01-01 00:00:00.000000', UTC_TIMESTAMP(6)),
       ('00000000000000000000000447', 'ONBOARDED', 1, '只以真实 OnboardingEvent 计数。', 'ONBOARDED', '2026-01-01 00:00:00.000000', UTC_TIMESTAMP(6));

SET
FOREIGN_KEY_CHECKS = @HR_AI_OLD_FOREIGN_KEY_CHECKS;

-- 模拟邮件来源与精确简历版本关联，不创建招聘申请。
CREATE TABLE `demand_mail_source_message`
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模拟邮件来源';

CREATE TABLE `demand_jd_resume_source`
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件与简历版本关联';

-- 公司统一评分基线：保留旧运行冻结的方案，不覆盖既有数据。
INSERT INTO matching_jd_match_plan(id,name,current_version_no,create_id)
SELECT '00000000000000000000000011','示例默认五维评分',1,'00000000000000000000000000'
WHERE NOT EXISTS (SELECT 1 FROM matching_jd_match_plan WHERE id='00000000000000000000000011');
INSERT INTO matching_controlled_criterion_plan_version(id,version_no,plan_max_bonus_points,create_id)
SELECT '00000000000000000000000013',1,0,'00000000000000000000000000'
WHERE NOT EXISTS (SELECT 1 FROM matching_controlled_criterion_plan_version WHERE id='00000000000000000000000013');
INSERT INTO matching_jd_match_plan_version(id,plan_id,version_no,status,active_slot,controlled_criterion_plan_version_id,
  skills_enabled,role_enabled,years_enabled,responsibility_enabled,industry_project_enabled,configured_by_user_id,activated_by_user_id,activated_at)
SELECT '00000000000000000000000012','00000000000000000000000011',1,'ACTIVE',1,'00000000000000000000000013',
  TRUE,TRUE,TRUE,TRUE,TRUE,'00000000000000000000000000','00000000000000000000000000',CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM matching_jd_match_plan_version WHERE id='00000000000000000000000012');
INSERT INTO matching_weight_profile(id,owner_user_id,name,status,current_version_no,create_id)
SELECT '00000000000000000000000021',NULL,'示例默认权重','ACTIVE',1,'00000000000000000000000000'
WHERE NOT EXISTS (SELECT 1 FROM matching_weight_profile WHERE id='00000000000000000000000021');
INSERT INTO matching_weight_profile_version(id,profile_id,version_no,skills_weight,role_weight,years_weight,responsibility_weight,industry_project_weight,create_id)
SELECT '00000000000000000000000022','00000000000000000000000021',1,20,20,20,20,20,'00000000000000000000000000'
WHERE NOT EXISTS (SELECT 1 FROM matching_weight_profile_version WHERE id='00000000000000000000000022');

CREATE TABLE `demand_jd_source`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '系统生成来源 ID',
    jd_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '确切原文版本',
    source_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MANUAL 或 MAIL',
    source_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '系统生成幂等来源指纹',
    mail_source_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '邮件来源，人工录入为空',
    owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源收件人或录入人',
    ext TEXT NULL COMMENT 'JSON 扩展信息',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    create_name VARCHAR(64) NULL COMMENT '创建人名称',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    update_name VARCHAR(64) NULL COMMENT '更新人名称',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_jd_source_origin UNIQUE (owner_user_id, source_type, source_key),
    CONSTRAINT fk_jd_source_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_jd_source_mail FOREIGN KEY (mail_source_id) REFERENCES demand_mail_source_message (id) ON DELETE RESTRICT,
    CONSTRAINT ck_jd_source_type CHECK (source_type IN ('MANUAL', 'MAIL')),
    INDEX ix_jd_source_version (jd_version_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 版本来源追踪';


-- 早期已建预约表补列使用 20260909_04 升级，不能仅重跑 CREATE TABLE IF NOT EXISTS。
CREATE TABLE `recruitment_follow_up_plan`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预约 ID',
    application_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '招聘活动 ID',
    starts_at DATETIME(6) NOT NULL COMMENT 'HR 确认的沟通开始时间',
    ends_at DATETIME(6) NOT NULL COMMENT 'HR 确认的沟通结束时间',
    channel VARCHAR(32) NOT NULL COMMENT '沟通渠道',
    note VARCHAR(500) NOT NULL COMMENT '沟通目的及安排',
    status VARCHAR(20) NOT NULL COMMENT '预约状态',
    result_reason VARCHAR(500) NULL COMMENT '最近完成或取消的实际结果说明',
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
    INDEX ix_follow_up_plan_calendar (starts_at, id),
    INDEX ix_follow_up_plan_application (application_id, status, id),
    CONSTRAINT ck_follow_up_plan_status CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_follow_up_plan_time CHECK (ends_at > starts_at),
    CONSTRAINT fk_follow_up_plan_application FOREIGN KEY (application_id) REFERENCES recruitment_application (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HR 确认的候选人沟通预约';

CREATE TABLE `recruitment_follow_up_plan_history`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预约审计 ID',
    plan_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预约 ID',
    action VARCHAR(32) NOT NULL COMMENT '操作类型',
    before_snapshot TEXT NULL COMMENT '操作前预约完整快照',
    after_snapshot TEXT NOT NULL COMMENT '操作后预约完整快照',
    reason VARCHAR(500) NOT NULL COMMENT '操作原因',
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
    INDEX ix_follow_up_plan_history (plan_id, create_time, id),
    CONSTRAINT fk_follow_up_plan_history_plan FOREIGN KEY (plan_id) REFERENCES recruitment_follow_up_plan (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沟通预约不可变更的操作审计';
