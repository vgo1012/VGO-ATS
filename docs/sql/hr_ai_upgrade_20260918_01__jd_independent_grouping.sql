-- 版本：20260918_01；独立 JD 与三位分组编号。
-- 前提：MySQL 8，完成 20260917_06；选定目标库，停止旧应用及 Worker 写入，备份 JD、字典、组合序列和全局序列。
-- 数据影响：岗位/地区仅用于分组，不再以名称和区分说明合并或拒绝独立 JD；删除身份生成列及其唯一索引。
-- 身份列是可重算派生值，无业务数据；不删除 JD、不合并负责人/HC、不清空历史区分说明、不修改任何完整 JD 编号。
-- 旧两位维度补零为三位，旧四位组合键逐段补零为六位；保留组合 `last_value` 及全局计数器，禁止回退或清零。
-- 重跑：先检查当前数据库元数据和数据，逐条 DDL 后可续跑；每个归一 UPDATE 原子完成，已规范值保持不变。
-- 恢复：失败后保持停写，核对已完成对象后重跑；不要部署依赖旧两位列或全局身份键的旧应用。
-- 如必须回退结构，先备份新增 JD，人工处理现已合法的重复名称；仅恢复到升级前完整备份，禁止删 JD 来恢复唯一约束。
SET NAMES utf8mb4;

SET @jd_grouping_sql = IF(
    DATABASE() IS NOT NULL AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('demand_jd','demand_jd_code_option','demand_jd_code_pair_sequence','demand_jd_code_sequence') AND ENGINE='InnoDB')=4,
    'DO 0', 'SELECT `JD independent grouping upgrade precondition failed; inspect schema and data`');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_code_option' AND COLUMN_NAME='code' AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH IN (2,3) AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')=1,
    'DO 0', 'SELECT `JD independent grouping upgrade precondition failed; inspect schema and data`');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_code_pair_sequence' AND COLUMN_NAME='id' AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH IN (4,6) AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')=1,
    'DO 0', 'SELECT `JD independent grouping upgrade precondition failed; inspect schema and data`');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND COLUMN_NAME='job_type_code' AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH IN (2,3) AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')=1,
    'DO 0', 'SELECT `JD independent grouping upgrade precondition failed; inspect schema and data`');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND COLUMN_NAME='work_region_code' AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH IN (2,3) AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')=1,
    'DO 0', 'SELECT `JD independent grouping upgrade precondition failed; inspect schema and data`');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    NOT EXISTS (SELECT 1 FROM demand_jd_code_option WHERE code NOT REGEXP '^[0-9]{2,3}$' OR CAST(code AS UNSIGNED)=0) AND NOT EXISTS (SELECT 1 FROM demand_jd WHERE (job_type_code IS NOT NULL AND (job_type_code NOT REGEXP '^[0-9]{2,3}$' OR CAST(job_type_code AS UNSIGNED)=0)) OR (work_region_code IS NOT NULL AND (work_region_code NOT REGEXP '^[0-9]{2,3}$' OR CAST(work_region_code AS UNSIGNED)=0))) AND NOT EXISTS (SELECT 1 FROM demand_jd_code_pair_sequence WHERE id NOT REGEXP '^([0-9]{4}|[0-9]{6})$' OR `last_value` < 0),
    'DO 0', 'SELECT `JD independent grouping upgrade precondition failed; inspect schema and data`');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    NOT EXISTS (SELECT 1 FROM demand_jd_code_option GROUP BY kind, LPAD(code,3,'0') HAVING COUNT(*)>1) AND NOT EXISTS (SELECT 1 FROM demand_jd_code_pair_sequence GROUP BY CASE WHEN CHAR_LENGTH(id)=4 THEN CONCAT('0',LEFT(id,2),'0',RIGHT(id,2)) ELSE id END HAVING COUNT(*)>1),
    'DO 0', 'SELECT `normalized JD dimension or pair keys conflict; reconcile manually without resetting sequences`');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_code_option' AND CONSTRAINT_NAME='ck_jd_code_option_code' AND CONSTRAINT_TYPE='CHECK')=1,
    'ALTER TABLE demand_jd_code_option DROP CHECK ck_jd_code_option_code', 'DO 0');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_code_option' AND COLUMN_NAME='code') < 3,
    'ALTER TABLE demand_jd_code_option MODIFY COLUMN code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''维度内部稳定三位码 001 至 999''', 'DO 0');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_code_pair_sequence' AND COLUMN_NAME='id') < 6,
    'ALTER TABLE demand_jd_code_pair_sequence MODIFY COLUMN id CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''岗位类型三位码与工作区域三位码组合''', 'DO 0');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND COLUMN_NAME='job_type_code') < 3,
    'ALTER TABLE demand_jd MODIFY COLUMN job_type_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''当前岗位类型代码，历史可空，调整不改编号''', 'DO 0');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND COLUMN_NAME='work_region_code') < 3,
    'ALTER TABLE demand_jd MODIFY COLUMN work_region_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''当前工作区域代码，历史可空，调整不改编号''', 'DO 0');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

UPDATE demand_jd_code_option SET code=LPAD(code,3,'0'), update_time=update_time WHERE CHAR_LENGTH(code)=2;
UPDATE demand_jd_code_pair_sequence SET id=CONCAT('0',LEFT(id,2),'0',RIGHT(id,2)), update_time=update_time WHERE CHAR_LENGTH(id)=4;
UPDATE demand_jd SET job_type_code=LPAD(job_type_code,3,'0'), work_region_code=LPAD(work_region_code,3,'0'), update_time=update_time WHERE CHAR_LENGTH(job_type_code)=2 OR CHAR_LENGTH(work_region_code)=2;

SET @jd_grouping_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd_code_option' AND CONSTRAINT_NAME='ck_jd_code_option_code')=0,
    'ALTER TABLE demand_jd_code_option ADD CONSTRAINT ck_jd_code_option_code CHECK (CHAR_LENGTH(code) = 3 AND code <> ''000'' AND ASCII(SUBSTRING(code, 1, 1)) BETWEEN 48 AND 57 AND ASCII(SUBSTRING(code, 2, 1)) BETWEEN 48 AND 57 AND ASCII(SUBSTRING(code, 3, 1)) BETWEEN 48 AND 57)', 'DO 0');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND INDEX_NAME='uk_jd_active_identity')>0,
    'ALTER TABLE demand_jd DROP INDEX uk_jd_active_identity', 'DO 0');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='demand_jd' AND COLUMN_NAME='active_identity_key' AND EXTRA LIKE '%GENERATED%')=1,
    'ALTER TABLE demand_jd DROP COLUMN active_identity_key', 'DO 0');
PREPARE jd_grouping_stmt FROM @jd_grouping_sql;
EXECUTE jd_grouping_stmt;
DEALLOCATE PREPARE jd_grouping_stmt;

SET @jd_grouping_sql = NULL;
