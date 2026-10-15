-- 版本：20260907_05；来源 JD 草稿可在正式招聘需求信息补齐前保存、解析。
-- 前置：MySQL 8，已部署完整基线及此前升级，选择目标数据库并停止应用后执行。
-- 仅新增可空来源列/唯一索引并放宽需求外键列非空；保留原外键和全部历史数据。
-- 同一收件人的同标题正文幂等复用，普通正式 JD 的空指纹不互相冲突。
-- 每步检查当前数据库元数据，可重复执行及从部分完成恢复；缺少 demand_jd 时明确失败。
SET NAMES utf8mb4;

SET @hr_ai_upgrade_20260907_05_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'source_owner_department_id'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN source_owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''来源草稿归属部门''');
PREPARE hr_ai_upgrade_20260907_05_stmt FROM @hr_ai_upgrade_20260907_05_sql;
EXECUTE hr_ai_upgrade_20260907_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_05_stmt;

SET @hr_ai_upgrade_20260907_05_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'source_fingerprint'),
 'DO 0', 'ALTER TABLE demand_jd ADD COLUMN source_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''来源标题和正文指纹''');
PREPARE hr_ai_upgrade_20260907_05_stmt FROM @hr_ai_upgrade_20260907_05_sql;
EXECUTE hr_ai_upgrade_20260907_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_05_stmt;

SET @hr_ai_upgrade_20260907_05_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'demand_jd' AND COLUMN_NAME = 'demand_id' AND IS_NULLABLE = 'YES'),
 'DO 0', 'ALTER TABLE demand_jd MODIFY COLUMN demand_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''招聘需求 ID，来源草稿待补充''');
PREPARE hr_ai_upgrade_20260907_05_stmt FROM @hr_ai_upgrade_20260907_05_sql;
EXECUTE hr_ai_upgrade_20260907_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_05_stmt;

SET @hr_ai_upgrade_20260907_05_sql = IF(
 EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'demand_jd' AND INDEX_NAME = 'uk_demand_jd_source'),
 'DO 0', 'ALTER TABLE demand_jd ADD CONSTRAINT uk_demand_jd_source UNIQUE (create_id, source_fingerprint)');
PREPARE hr_ai_upgrade_20260907_05_stmt FROM @hr_ai_upgrade_20260907_05_sql;
EXECUTE hr_ai_upgrade_20260907_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_05_stmt;
SET @hr_ai_upgrade_20260907_05_sql = NULL;

