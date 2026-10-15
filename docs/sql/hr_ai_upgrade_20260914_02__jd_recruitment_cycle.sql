-- 版本：20260914_02；为 JD 增加招聘周期（天）。
-- 前提：MySQL 8，已部署包含 demand_jd 的收敛基线及 20260914_01，客户端已选择目标数据库。
-- 数据影响：新增可空列；历史 JD 不猜测周期，HR 可在编辑 JD 时补充。
-- 重跑与部分完成：每一步先检查元数据，已存在且定义兼容时跳过。
-- 恢复：应用回退前可保留此列；若必须删除，先备份 demand_jd 并确认没有新版应用运行。
SET NAMES utf8mb4;

SET @cycle_sql = IF(DATABASE() IS NOT NULL AND
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='demand_jd')=1,
 'DO 0', 'SELECT `JD recruitment cycle upgrade requires demand_jd in the selected database`');
PREPARE cycle_stmt FROM @cycle_sql;
EXECUTE cycle_stmt;
DEALLOCATE PREPARE cycle_stmt;

SET @cycle_sql = IF(
 (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='demand_jd' AND COLUMN_NAME='recruitment_cycle_days')=0,
 'ALTER TABLE demand_jd ADD COLUMN recruitment_cycle_days INT NULL COMMENT ''招聘周期（天），历史自动来源待 HR 补充'' AFTER headcount',
 IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='recruitment_cycle_days' AND DATA_TYPE='int'
     AND IS_NULLABLE='YES')=1, 'DO 0',
    'SELECT `existing demand_jd.recruitment_cycle_days has an incompatible definition`'));
PREPARE cycle_stmt FROM @cycle_sql;
EXECUTE cycle_stmt;
DEALLOCATE PREPARE cycle_stmt;

SET @cycle_sql = IF(
 (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
  AND TABLE_NAME='demand_jd' AND CONSTRAINT_NAME='ck_demand_jd_recruitment_cycle')=0,
 'ALTER TABLE demand_jd ADD CONSTRAINT ck_demand_jd_recruitment_cycle CHECK (recruitment_cycle_days IS NULL OR recruitment_cycle_days BETWEEN 1 AND 3650)',
 'DO 0');
PREPARE cycle_stmt FROM @cycle_sql;
EXECUTE cycle_stmt;
DEALLOCATE PREPARE cycle_stmt;
SET @cycle_sql = NULL;
