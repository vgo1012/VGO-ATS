-- 版本：20260908_14；持久化普通异步任务的原始请求关联 ID。
-- 前提：MySQL 8，已选定业务库且 reporting_async_task 已存在；先执行本升级再部署使用 trace_id 的应用。
-- 数据影响：仅增加可空列，不更新历史任务、尝试、状态或版本；未知历史 trace 保持 NULL，不伪造关联。
-- 可重跑和部分完成：先核对选定库的列元数据；已有兼容列时不执行 DDL，不覆盖已有 trace。
-- 恢复：应用回退可保留该列；如需删列，先停用读写该列的版本并备份关联数据，再人工执行删除。
SET NAMES utf8mb4;

SET @hr_ai_task_trace_sql = IF(
    DATABASE() IS NOT NULL AND (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_async_task')=1,
    'DO 0', 'SELECT `async task trace requires reporting_async_task in the selected database`');
PREPARE hr_ai_task_trace_stmt FROM @hr_ai_task_trace_sql;
EXECUTE hr_ai_task_trace_stmt;
DEALLOCATE PREPARE hr_ai_task_trace_stmt;

SET @hr_ai_task_trace_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='reporting_async_task' AND COLUMN_NAME='trace_id')=0,
    'ALTER TABLE reporting_async_task ADD COLUMN trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''创建任务时的请求关联 ID，历史任务可为空'' AFTER id',
    IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='reporting_async_task' AND COLUMN_NAME='trace_id'
      AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=64 AND COLLATION_NAME='ascii_bin'
      AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='')=1,
      'DO 0', 'SELECT `existing async task trace column is incompatible; inspect before upgrading`'));
PREPARE hr_ai_task_trace_stmt FROM @hr_ai_task_trace_sql;
EXECUTE hr_ai_task_trace_stmt;
DEALLOCATE PREPARE hr_ai_task_trace_stmt;
