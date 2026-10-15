-- 版本：20260908_03；用途：补齐旧版排队任务直接取消时遗漏的完成时间。
-- 前提：MySQL 8，选定目标库，reporting_async_task 已具备租约及标准审计列，使用 InnoDB。
-- 执行前停止旧版应用和 Worker，备份候选任务行；同一连接顺序执行整个文件。
-- 仅处理 CANCELLED、attempt_count=0、finished_at IS NULL 的任务；不修改业务作业或有领取历史的任务。
-- 以原 update_time（旧取消 UPDATE 写入的审计时间）回填 finished_at，不推算真实执行耗时。
-- 如历史记录曾被手工修改审计时间，应先人工核对，不能把该值当作可靠取消时间直接执行。
-- 同时清除 retryable，递增 version，并将更新审计标记为系统本次修复；保留取消原因及创建审计。
-- 数据异常时中止；一条 InnoDB UPDATE 原子写入，完成后重跑为空操作，可在部分记录已修复后继续。
-- 回退需按执行前备份核对并恢复上述字段；不回退任务状态，不删除记录，不改结构或 Flyway 历史。
SET NAMES utf8mb4;

SET @hr_ai_upgrade_20260908_03_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_async_task' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_async_task'
       AND COLUMN_NAME IN ('id','status','attempt_count','lease_until','finished_at','create_time',
                           'update_time','version','update_id','update_name','retryable'))=11,
    'DO 0',
    'SELECT `queued cancellation repair requires the existing task lease and audit columns on InnoDB`'
);
PREPARE hr_ai_upgrade_20260908_03_stmt FROM @hr_ai_upgrade_20260908_03_sql;
EXECUTE hr_ai_upgrade_20260908_03_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_03_stmt;

SET @hr_ai_upgrade_20260908_03_sql = IF(
    (SELECT COUNT(*) FROM reporting_async_task
     WHERE status='CANCELLED' AND attempt_count=0 AND finished_at IS NULL
       AND (lease_until IS NOT NULL OR update_time IS NULL OR create_time IS NULL
            OR update_time<create_time OR update_time>CURRENT_TIMESTAMP(6)
            OR version IS NULL OR version<0 OR version>=2147483647))=0,
    'DO 0',
    'SELECT `queued cancellation history requires manual review before timestamp repair`'
);
PREPARE hr_ai_upgrade_20260908_03_stmt FROM @hr_ai_upgrade_20260908_03_sql;
EXECUTE hr_ai_upgrade_20260908_03_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_03_stmt;
SET @hr_ai_upgrade_20260908_03_sql = NULL;

UPDATE reporting_async_task
SET finished_at=update_time,
    retryable=FALSE,
    version=version+1,
    update_id='00000000000000000000000000',
    update_name=NULL,
    update_time=CURRENT_TIMESTAMP(6)
WHERE status='CANCELLED' AND attempt_count=0 AND finished_at IS NULL;

SELECT ROW_COUNT() AS repaired_unclaimed_cancelled_tasks;
