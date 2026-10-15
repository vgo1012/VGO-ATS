-- 版本：20260914_01；为 JD 增加 HC（招聘编制）。
-- 前提：MySQL 8，已部署包含 demand_jd 的收敛基线，客户端已选择目标数据库。
-- 数据影响：新增可空列；历史 JD 不猜测 HC，HR 补充前应用层阻止新增招聘流程。
-- 重跑与部分完成：每一步先检查元数据，已存在且定义兼容时跳过。
-- 恢复：应用回退前可保留此列；若必须删除，先确认没有新版应用运行并备份 demand_jd。
SET NAMES utf8mb4;

SET @hc_sql = IF(DATABASE() IS NOT NULL AND
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='demand_jd')=1,
 'DO 0', 'SELECT `JD HC upgrade requires demand_jd in the selected database`');
PREPARE hc_stmt FROM @hc_sql;
EXECUTE hc_stmt;
DEALLOCATE PREPARE hc_stmt;

SET @hc_sql = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='demand_jd' AND COLUMN_NAME='headcount')=0,
 'ALTER TABLE demand_jd ADD COLUMN headcount INT NULL COMMENT ''招聘编制，历史自动来源待 HR 补充'' AFTER recruitment_type',
 IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='headcount' AND DATA_TYPE='int'
     AND IS_NULLABLE='YES')=1, 'DO 0',
    'SELECT `existing demand_jd.headcount has an incompatible definition`'));
PREPARE hc_stmt FROM @hc_sql;
EXECUTE hc_stmt;
DEALLOCATE PREPARE hc_stmt;

SET @hc_sql = IF(
 (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='demand_jd' AND CONSTRAINT_NAME='ck_demand_jd_headcount')=0,
 'ALTER TABLE demand_jd ADD CONSTRAINT ck_demand_jd_headcount CHECK (headcount IS NULL OR headcount BETWEEN 1 AND 10000)',
 'DO 0');
PREPARE hc_stmt FROM @hc_sql;
EXECUTE hc_stmt;
DEALLOCATE PREPARE hc_stmt;
SET @hc_sql = NULL;
