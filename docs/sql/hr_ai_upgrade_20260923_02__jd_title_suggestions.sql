-- 版本：20260923_02；新增 JD 岗位名称建议与超级管理员审核队列。
-- 前提：MySQL 8，已部署 20260923_01；先核对目标库并备份 demand_jd 与相关审计表。
-- 数据影响：新增独立建议表和 3 个合成通用岗位示例；不改写现有 JD、编号和区域字典。
-- 重跑与部分完成：建表及初始名称导入均可重跑；已有名称和审核状态不覆盖。
-- 恢复：保留建议表及审核历史；回退应用时旧应用不读取该表。
SET NAMES utf8mb4;

SET @jd_title_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('demand_jd','demand_jd_code_option','demand_audit_event') AND ENGINE='InnoDB')=3,
    'DO 0', 'SELECT `JD title suggestion upgrade requires demand JD baseline`');
PREPARE jd_title_stmt FROM @jd_title_sql;
EXECUTE jd_title_stmt;
DEALLOCATE PREPARE jd_title_stmt;

CREATE TABLE IF NOT EXISTS `demand_jd_title_suggestion`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY COMMENT '名称建议 ID',
    name VARCHAR(80) NOT NULL COMMENT '岗位名称，按数据库排序规则去重',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PENDING、APPROVED 或 REJECTED',
    submitted_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '最早提交此名称的 JD 创建人；初始名称为空',
    reviewed_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '审核人',
    reviewed_at TIMESTAMP(6) NULL COMMENT '审核时间',
    rejection_reason VARCHAR(500) NULL COMMENT '驳回原因',
    `ext` TEXT NULL COMMENT 'JSON 扩展信息',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_jd_title_suggestion_name UNIQUE (name),
    CONSTRAINT ck_jd_title_suggestion_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_jd_title_rejection CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    INDEX ix_jd_title_review_queue (status, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='JD 岗位名称共享建议与审核';

SET @jd_title_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_title_suggestion' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_title_suggestion')=16 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_title_suggestion' AND INDEX_NAME='uk_jd_title_suggestion_name'
     AND COLUMN_NAME='name' AND NON_UNIQUE=0)=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_title_suggestion' AND CONSTRAINT_NAME='ck_jd_title_suggestion_status'
     AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES')=1,
    'DO 0', 'SELECT `existing JD title suggestion table is incompatible; inspect before upgrade`');
PREPARE jd_title_stmt FROM @jd_title_sql;
EXECUTE jd_title_stmt;
DEALLOCATE PREPARE jd_title_stmt;

-- VGO 首次公开发行的合成通用岗位示例，不包含公司岗位目录。
INSERT INTO demand_jd_title_suggestion (id, name, status) VALUES
('00000000000000000000000001', '示例开发工程师', 'APPROVED'),
('00000000000000000000000002', '示例产品经理', 'APPROVED'),
('00000000000000000000000003', '示例招聘专员', 'APPROVED')
ON DUPLICATE KEY UPDATE id = id;

SET @jd_title_sql = NULL;
