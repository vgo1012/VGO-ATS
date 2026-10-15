-- 版本：20260917_02；新增企业微信来源成员与本地账号的显式一对一关联。
-- 前提：MySQL 8，已执行 20260916_01；执行前备份 identity_wecom_member 与 identity_user_account。
-- 数据影响：仅新增空关联表，不创建账号、不修改角色或组织。
-- 重跑：表已存在时先校验关键唯一键；结构兼容则跳过。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS identity_wecom_account_link (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源成员与本地账号关联 ID',
    wecom_member_id VARCHAR(36) NOT NULL COMMENT '企业微信来源成员内部 ID',
    user_account_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本地登录账号 ID',
    linked_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行关联的管理员账号 ID',
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
    UNIQUE KEY uk_wecom_account_link_member (wecom_member_id),
    UNIQUE KEY uk_wecom_account_link_account (user_account_id),
    CONSTRAINT fk_wecom_account_link_member FOREIGN KEY (wecom_member_id) REFERENCES identity_wecom_member(id),
    CONSTRAINT fk_wecom_account_link_account FOREIGN KEY (user_account_id) REFERENCES identity_user_account(id),
    CONSTRAINT fk_wecom_account_link_actor FOREIGN KEY (linked_by_user_id) REFERENCES identity_user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业微信来源成员与本地登录账号的一对一显式关联';

SET @wecom_link_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_wecom_account_link'
     AND INDEX_NAME IN ('uk_wecom_account_link_member','uk_wecom_account_link_account')
     AND NON_UNIQUE=0)=2,
    'DO 0', 'SELECT `existing identity_wecom_account_link table is incompatible; inspect before upgrading`');
PREPARE wecom_link_stmt FROM @wecom_link_sql;
EXECUTE wecom_link_stmt;
DEALLOCATE PREPARE wecom_link_stmt;
SET @wecom_link_sql = NULL;

CREATE TABLE IF NOT EXISTS identity_wecom_department_link (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源部门与本地部门关联 ID',
    wecom_department_id VARCHAR(36) NOT NULL COMMENT '企业微信来源部门内部 ID',
    local_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本地组织部门 ID',
    linked_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行关联的管理员账号 ID',
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
    UNIQUE KEY uk_wecom_department_link_source (wecom_department_id),
    KEY idx_wecom_department_link_local (local_department_id),
    CONSTRAINT fk_wecom_department_link_source FOREIGN KEY (wecom_department_id) REFERENCES identity_wecom_department(id),
    CONSTRAINT fk_wecom_department_link_local FOREIGN KEY (local_department_id) REFERENCES identity_department(id),
    CONSTRAINT fk_wecom_department_link_actor FOREIGN KEY (linked_by_user_id) REFERENCES identity_user_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业微信来源部门与本地组织部门的显式归并关联';

SET @wecom_link_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_wecom_department_link'
     AND ((INDEX_NAME='uk_wecom_department_link_source' AND NON_UNIQUE=0)
       OR (INDEX_NAME='idx_wecom_department_link_local' AND NON_UNIQUE=1)))=2,
    'DO 0', 'SELECT `existing identity_wecom_department_link table is incompatible; inspect before upgrading`');
PREPARE wecom_link_stmt FROM @wecom_link_sql;
EXECUTE wecom_link_stmt;
DEALLOCATE PREPARE wecom_link_stmt;
SET @wecom_link_sql = NULL;
