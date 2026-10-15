-- 版本：20260909_01；JD 库独立于招聘需求，客户/归属/可见范围直接记录，版本冻结客户。
-- 前置：MySQL 8，已部署完整基线及此前升级；选择目标数据库，停止旧应用及 Worker 后执行。
-- 数据影响：新增可空列和逻辑删除标记；保留需求/岗位历史及外键；仅回填缺失的 JD 元数据。
-- 放宽应聘/推荐的 demand_id 和推荐 position_id 为可空，不删除、重编号或合并已有业务记录。
-- 每步检查选中数据库元数据，支持重复执行和中断接续。执行前备份；恢复请还原备份并使用对应旧应用。
SET NAMES utf8mb4;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 DATABASE() IS NOT NULL AND
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN
  ('demand_jd','demand_jd_version','demand_client','demand_recruitment_demand',
   'recruitment_application','submission_client_submission','demand_mail_source_message','reporting_async_task')) = 8,
 'DO 0', 'SELECT `jd catalog upgrade requires selected database and prerequisite tables`');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'code'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''系统生成的稳定 JD 编号''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'client_id'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN client_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''JD 客户，可空表示内部招聘''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'owner_user_id'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''JD 归属 HR''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'owner_department_id'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''JD 归属部门''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'recruitment_type'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN recruitment_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''JD 招聘类型''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'public_visible'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN public_visible BOOLEAN NOT NULL DEFAULT FALSE COMMENT ''管理员维护的公共 JD''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'distinction'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN distinction VARCHAR(180) NULL COMMENT ''同名岗位区分说明''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'deleted'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT ''逻辑删除，保留历史和去重记录''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd_version' AND COLUMN_NAME = 'client_id'),
 'DO 0', 'ALTER TABLE demand_jd_version ADD COLUMN client_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''版本冻结客户''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd_version' AND COLUMN_NAME = 'recruitment_type'),
 'DO 0', 'ALTER TABLE demand_jd_version ADD COLUMN recruitment_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''版本冻结招聘类型，内部招聘的空客户不可回填''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

-- 冻结存量客户和归属；非空招聘类型标志版本已经迁移，重跑不会改写其客户快照。
UPDATE demand_jd j LEFT JOIN demand_recruitment_demand d ON d.id = j.demand_id
SET j.code = COALESCE(j.code, CONCAT('JD-', j.id)),
    j.client_id = COALESCE(j.client_id, d.client_id),
    j.owner_user_id = COALESCE(j.owner_user_id, d.owner_user_id, j.create_id),
    j.owner_department_id = COALESCE(j.owner_department_id, d.owner_department_id, j.source_owner_department_id),
    j.recruitment_type = COALESCE(j.recruitment_type, d.recruitment_type, 'INTERNAL')
WHERE j.recruitment_type IS NULL;
UPDATE demand_jd_version v JOIN demand_jd j ON j.id = v.jd_id
SET v.client_id = j.client_id, v.recruitment_type = j.recruitment_type
WHERE v.recruitment_type IS NULL;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND CONSTRAINT_NAME = 'uk_demand_jd_code'),
 'DO 0', 'ALTER TABLE demand_jd ADD CONSTRAINT uk_demand_jd_code UNIQUE (code)');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd' AND CONSTRAINT_NAME = 'fk_demand_jd_client'),
 'DO 0', 'ALTER TABLE demand_jd ADD CONSTRAINT fk_demand_jd_client FOREIGN KEY (client_id) REFERENCES demand_client (id) ON DELETE RESTRICT');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd_version' AND CONSTRAINT_NAME = 'fk_demand_jd_version_client'),
 'DO 0', 'ALTER TABLE demand_jd_version ADD CONSTRAINT fk_demand_jd_version_client FOREIGN KEY (client_id) REFERENCES demand_client (id) ON DELETE RESTRICT');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recruitment_application' AND COLUMN_NAME = 'demand_id' AND IS_NULLABLE = 'YES'),
 'DO 0', 'ALTER TABLE recruitment_application MODIFY COLUMN demand_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''历史关联保留，新业务直接关联 JD 版本''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submission_client_submission' AND COLUMN_NAME = 'demand_id' AND IS_NULLABLE = 'YES'),
 'DO 0', 'ALTER TABLE submission_client_submission MODIFY COLUMN demand_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''历史关联保留，新业务直接关联 JD 版本''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submission_client_submission' AND COLUMN_NAME = 'position_id' AND IS_NULLABLE = 'YES'),
 'DO 0', 'ALTER TABLE submission_client_submission MODIFY COLUMN position_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''历史关联保留，新业务直接关联 JD 版本''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd_version' AND COLUMN_NAME = 'content_owner_user_id'), 'DO 0', 'ALTER TABLE demand_jd_version ADD COLUMN content_owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''内容去重归属人''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd_version' AND COLUMN_NAME = 'content_fingerprint'), 'DO 0', 'ALTER TABLE demand_jd_version ADD COLUMN content_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''版本内容指纹，独立新增为空''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd_version' AND INDEX_NAME = 'uk_jd_version_content'), 'DO 0', 'ALTER TABLE demand_jd_version ADD CONSTRAINT uk_jd_version_content UNIQUE (content_owner_user_id, content_fingerprint)');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd_source'), 'DO 0', 'CREATE TABLE `demand_jd_source`
(
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT ''系统生成来源 ID'',
    jd_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''确切原文版本'',
    source_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''MANUAL 或 MAIL'',
    source_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''系统生成幂等来源指纹'',
    mail_source_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''邮件来源，人工录入为空'',
    owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''来源收件人或录入人'',
    ext TEXT NULL COMMENT ''JSON 扩展信息'',
    remark VARCHAR(255) NULL COMMENT ''备注'',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''创建人 ID'',
    create_name VARCHAR(64) NULL COMMENT ''创建人名称'',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''创建时间'',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''更新人 ID'',
    update_name VARCHAR(64) NULL COMMENT ''更新人名称'',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT ''更新时间'',
    version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号'',
    CONSTRAINT uk_jd_source_origin UNIQUE (owner_user_id, source_type, source_key),
    CONSTRAINT fk_jd_source_version FOREIGN KEY (jd_version_id) REFERENCES demand_jd_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_jd_source_mail FOREIGN KEY (mail_source_id) REFERENCES demand_mail_source_message (id) ON DELETE RESTRICT,
    CONSTRAINT ck_jd_source_type CHECK (source_type IN (''MANUAL'', ''MAIL'')),
    INDEX ix_jd_source_version (jd_version_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''JD 版本来源追踪''');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reporting_async_task' AND INDEX_NAME = 'ix_reporting_task_resource'), 'DO 0', 'ALTER TABLE reporting_async_task ADD INDEX ix_reporting_task_resource (task_type, resource_id, create_time, id)');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

-- 同名对象若来自中断执行或人工误建，必须完整兼容，不能仅凭名称宣告升级完成。
SET @hr_ai_upgrade_20260909_01_sql = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND
   ((COLUMN_NAME='code' AND COLUMN_TYPE='varchar(64)' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES') OR
    (COLUMN_NAME IN ('client_id','owner_user_id','owner_department_id') AND COLUMN_TYPE='char(26)' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES') OR
    (COLUMN_NAME='recruitment_type' AND COLUMN_TYPE='varchar(16)' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES') OR
    (COLUMN_NAME IN ('public_visible','deleted') AND COLUMN_TYPE='tinyint(1)' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0') OR
    (COLUMN_NAME='distinction' AND COLUMN_TYPE='varchar(180)' AND IS_NULLABLE='YES'))) = 8 AND
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_version' AND
   ((COLUMN_NAME='client_id' AND COLUMN_TYPE='char(26)' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES') OR
    (COLUMN_NAME='recruitment_type' AND COLUMN_TYPE='varchar(16)' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES') OR
    (COLUMN_NAME='content_owner_user_id' AND COLUMN_TYPE='char(26)' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES') OR
    (COLUMN_NAME='content_fingerprint' AND COLUMN_TYPE='char(64)' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES'))) = 4 AND
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND
   ((TABLE_NAME='recruitment_application' AND COLUMN_NAME='demand_id' AND COLUMN_TYPE='char(26)' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES') OR
    (TABLE_NAME='submission_client_submission' AND COLUMN_NAME IN ('demand_id','position_id') AND COLUMN_TYPE='char(26)' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES'))) = 3,
 'DO 0', 'SELECT `existing jd catalog columns have incompatible definitions`');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND
   INDEX_NAME='uk_demand_jd_code' AND COLUMN_NAME='code' AND SEQ_IN_INDEX=1 AND NON_UNIQUE=0 AND SUB_PART IS NULL) = 1 AND
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_version' AND
   INDEX_NAME='uk_jd_version_content' AND NON_UNIQUE=0 AND SUB_PART IS NULL) = 2 AND
 (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND
   TABLE_NAME='demand_jd_version' AND INDEX_NAME='uk_jd_version_content') = 'content_owner_user_id,content_fingerprint' AND
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_async_task' AND
   INDEX_NAME='ix_reporting_task_resource') = 4 AND
 (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND
   TABLE_NAME='reporting_async_task' AND INDEX_NAME='ix_reporting_task_resource') = 'task_type,resource_id,create_time,id' AND
 (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND
   CONSTRAINT_NAME='fk_demand_jd_client' AND COLUMN_NAME='client_id' AND REFERENCED_TABLE_NAME='demand_client' AND REFERENCED_COLUMN_NAME='id') = 1 AND
 (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_version' AND
   CONSTRAINT_NAME='fk_demand_jd_version_client' AND COLUMN_NAME='client_id' AND REFERENCED_TABLE_NAME='demand_client' AND REFERENCED_COLUMN_NAME='id') = 1,
 'DO 0', 'SELECT `existing jd catalog indexes or foreign keys have incompatible definitions`');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_source' AND COLUMN_NAME IN
  ('id','jd_version_id','source_type','source_key','mail_source_id','owner_user_id','ext','remark','create_id','create_name',
   'create_time','update_id','update_name','update_time','version')) = 15 AND
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_source' AND
   INDEX_NAME='uk_jd_source_origin') = 3 AND
 (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND
   TABLE_NAME='demand_jd_source' AND INDEX_NAME='uk_jd_source_origin') = 'owner_user_id,source_type,source_key' AND
 (SELECT MIN(NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_source' AND
   INDEX_NAME='uk_jd_source_origin') = 0 AND
 (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_source' AND
   CONSTRAINT_NAME IN ('fk_jd_source_version','fk_jd_source_mail') AND REFERENCED_TABLE_NAME IS NOT NULL) = 2,
 'DO 0', 'SELECT `existing jd source table has incompatible definition`');
PREPARE hr_ai_upgrade_20260909_01_stmt FROM @hr_ai_upgrade_20260909_01_sql;
EXECUTE hr_ai_upgrade_20260909_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260909_01_stmt;

SET @hr_ai_upgrade_20260909_01_sql = NULL;
