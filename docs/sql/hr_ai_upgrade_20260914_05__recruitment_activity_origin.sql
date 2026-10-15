-- 版本：20260914_05；记录列表操作入口与实际业务事实的关联。
-- 前提：MySQL 8，已选中包含 recruitment_application 的收敛基线数据库；停止旧应用写入并完成备份。
-- 数据影响：只新增关联表；历史操作不补造按钮标题，也不重写业务事实。
-- 重跑与部分完成：检查选中数据库元数据；单条建表 DDL 原子执行，存在时跳过。
-- 恢复：可保留新增表回退应用；删除前先导出关联记录并停止所有新版写入方。
SET NAMES utf8mb4;
SET @activity_sql = IF(DATABASE() IS NOT NULL AND
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_application')=1,
 'DO 0','SELECT `activity origin upgrade requires recruitment_application in selected database`');
PREPARE activity_stmt FROM @activity_sql;
EXECUTE activity_stmt;
DEALLOCATE PREPARE activity_stmt;
SET @activity_sql = IF(
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_activity_origin')=0,
 'CREATE TABLE IF NOT EXISTS recruitment_activity_origin (
 id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''主键 ID'',
 application_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''招聘活动 ID'',
 source_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''业务事实类型'',
 source_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''业务事实 ID'',
 action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''操作按钮类型'',
 operation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''同次操作标识'',
 ext TEXT NULL COMMENT ''JSON 扩展信息'',
 remark VARCHAR(255) NULL COMMENT ''备注'',
 create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''创建人 ID'',
 create_name VARCHAR(64) NULL COMMENT ''创建人名称'',
 create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''创建时间'',
 update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''更新人 ID'',
 update_name VARCHAR(64) NULL COMMENT ''更新人名称'',
 update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT ''更新时间'',
 version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号'',
 PRIMARY KEY(id),
 UNIQUE KEY uk_activity_origin_source(source_type,source_id),
 INDEX ix_activity_origin_application(application_id,operation_id),
 CONSTRAINT ck_activity_origin_action CHECK(action IN (''PENDING'',''INTERVIEW'',''ENDED'',''ONBOARDED'',''BLACKLIST'')),
 CONSTRAINT fk_activity_origin_application FOREIGN KEY(application_id) REFERENCES recruitment_application(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''招聘操作入口与已有业务事实关联；旧历史不补造入口''', 'DO 0');
PREPARE activity_stmt FROM @activity_sql;
EXECUTE activity_stmt;
DEALLOCATE PREPARE activity_stmt;
SET @activity_sql = NULL;
