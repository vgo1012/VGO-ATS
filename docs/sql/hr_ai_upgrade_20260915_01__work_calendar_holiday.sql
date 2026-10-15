-- 版本：20260915_01；工作日历独立节假日类型。
-- 前提：MySQL 8，选中已执行 20260914_07 工作日历升级的 HR 数据库；备份并停止旧应用写入。
-- 数据影响：仅放宽日期类型约束，不修改历史日期，不把既有休息日推断为节假日。
-- 重跑：检查当前数据库约束；存在旧约束时单条原子 ALTER 替换，缺失约束时补齐，中断后可重跑。
-- 恢复：保留新约束与所有日历事实；存在 HOLIDAY 后不得直接回退旧应用，须先通过新版管理员流程更正并发布受影响年度。
SET NAMES utf8mb4;
SET @calendar_holiday_sql = IF(DATABASE() IS NOT NULL AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_work_calendar_day')=1,
 'DO 0', 'SELECT `holiday upgrade requires work calendar in selected database`');
PREPARE calendar_holiday_stmt FROM @calendar_holiday_sql;
EXECUTE calendar_holiday_stmt;
DEALLOCATE PREPARE calendar_holiday_stmt;

SET @calendar_holiday_sql = CASE
 WHEN (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_work_calendar_day_type' AND CHECK_CLAUSE LIKE '%HOLIDAY%')=1 THEN 'DO 0'
 WHEN (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_work_calendar_day' AND CONSTRAINT_NAME='ck_work_calendar_day_type' AND CONSTRAINT_TYPE='CHECK')=1 THEN
 'ALTER TABLE talent_work_calendar_day DROP CHECK ck_work_calendar_day_type, ADD CONSTRAINT ck_work_calendar_day_type CHECK (day_type IN (''WORKDAY'',''RESTDAY'',''HOLIDAY''))'
 ELSE 'ALTER TABLE talent_work_calendar_day ADD CONSTRAINT ck_work_calendar_day_type CHECK (day_type IN (''WORKDAY'',''RESTDAY'',''HOLIDAY''))'
 END;
PREPARE calendar_holiday_stmt FROM @calendar_holiday_sql;
EXECUTE calendar_holiday_stmt;
DEALLOCATE PREPARE calendar_holiday_stmt;
