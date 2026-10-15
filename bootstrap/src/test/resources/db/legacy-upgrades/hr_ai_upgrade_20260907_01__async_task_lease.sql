-- 版本：20260907_01；用途：为存量库补齐异步任务租约字段和恢复查询索引。
-- 适用：MySQL 8，已存在 reporting_async_task 表；执行前在客户端选择目标数据库。
-- 在同一连接中按顺序执行整个文件；部署时先停止旧版应用/Worker，再执行并启动新版。
-- 字段、索引分别检查，可重复执行，也可接续仅完成字段新增的执行。
-- 已有任务和租约值不改写；旧 RUNNING/CANCELLING 任务的 NULL 租约由新版恢复逻辑处理。
-- 本文件为手动升级脚本，不写 Flyway 历史；全量建库使用 hr_ai_complete_schema.sql。
SET NAMES utf8mb4;

SET @hr_ai_upgrade_20260907_01_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'reporting_async_task'
          AND COLUMN_NAME = 'lease_until'
    ),
    'DO 0',
    'ALTER TABLE reporting_async_task ADD COLUMN lease_until TIMESTAMP(6) NULL COMMENT ''当前处理租约到期时间'' AFTER started_at'
);
PREPARE hr_ai_upgrade_20260907_01_stmt FROM @hr_ai_upgrade_20260907_01_sql;
EXECUTE hr_ai_upgrade_20260907_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_01_stmt;

SET @hr_ai_upgrade_20260907_01_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'reporting_async_task'
          AND INDEX_NAME = 'ix_reporting_task_lease'
    ),
    'DO 0',
    'ALTER TABLE reporting_async_task ADD INDEX ix_reporting_task_lease (task_type, status, lease_until, id)'
);
PREPARE hr_ai_upgrade_20260907_01_stmt FROM @hr_ai_upgrade_20260907_01_sql;
EXECUTE hr_ai_upgrade_20260907_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_01_stmt;
SET @hr_ai_upgrade_20260907_01_sql = NULL;

-- 执行结果应显示 lease_until 和按 task_type/status/lease_until/id 排列的四行索引信息。
SHOW COLUMNS FROM reporting_async_task LIKE 'lease_until';
SHOW INDEX FROM reporting_async_task WHERE Key_name = 'ix_reporting_task_lease';
