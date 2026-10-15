-- 前置：选中业务库，并已应用 20260909_02 逐步跟进升级，两个预约表必须存在。
-- 数据影响：仅补齐早期已建表缺失的字段；历史结果保持 NULL，历史技术版本补为 0，不改写业务事实。
-- CREATE TABLE IF NOT EXISTS 不会补列；本脚本逐列检查元数据，支持重复执行和部分完成后接续。
-- 执行前备份两个预约表；回退应用时保留新增列即可，不删除历史预约与审计数据。
SET NAMES utf8mb4;

SET @hr_plan_columns_sql = IF(DATABASE() IS NOT NULL AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('recruitment_follow_up_plan','recruitment_follow_up_plan_history'))=2, 'DO 0', 'SELECT `follow up columns upgrade requires both prerequisite tables`');
PREPARE hr_plan_columns_stmt FROM @hr_plan_columns_sql;
EXECUTE hr_plan_columns_stmt;
DEALLOCATE PREPARE hr_plan_columns_stmt;

SET @hr_plan_columns_sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_follow_up_plan' AND COLUMN_NAME='result_reason'), 'DO 0', 'ALTER TABLE recruitment_follow_up_plan ADD COLUMN result_reason VARCHAR(500) NULL COMMENT ''最近完成或取消的实际结果说明''');
PREPARE hr_plan_columns_stmt FROM @hr_plan_columns_sql;
EXECUTE hr_plan_columns_stmt;
DEALLOCATE PREPARE hr_plan_columns_stmt;

SET @hr_plan_columns_sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='recruitment_follow_up_plan_history' AND COLUMN_NAME='version'), 'DO 0', 'ALTER TABLE recruitment_follow_up_plan_history ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号''');
PREPARE hr_plan_columns_stmt FROM @hr_plan_columns_sql;
EXECUTE hr_plan_columns_stmt;
DEALLOCATE PREPARE hr_plan_columns_stmt;
