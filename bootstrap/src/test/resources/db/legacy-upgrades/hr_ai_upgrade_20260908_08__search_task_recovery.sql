-- 版本：20260908_08；搜索失败任务人工恢复关联。
-- 前提：MySQL 8，已选业务库，已部署 20260908_05 的 reporting_async_task/reporting_outbox_event；停止旧版应用和 Worker。
-- 数据影响：只新建恢复关联表，原失败任务、尝试历史与事件内容均保留；不重置任何任务的次数或状态。
-- CREATE TABLE IF NOT EXISTS 检查选定库元数据；原子 DDL 完成后中断可以重跑，不覆盖已有恢复关联及审计。
-- 已存在同名表须满足字段、主键、唯一约束和外键定义，否则停止并要求核对；不自动删改历史数据。
-- 回退应用时保留本表；恢复新版后继续消费。新任务和原任务均有外键保护，需按保留策略完整归档关联后才能删除任务。
-- 完整空库使用 hr_ai_complete_schema.sql；本文件不修改 Flyway 历史。
SET NAMES utf8mb4;
SET @hr_ai_search_recovery_sql = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('reporting_async_task','reporting_outbox_event'))=2,
    'DO 0', 'SELECT `search recovery requires task and outbox tables`');
PREPARE hr_ai_search_recovery_stmt FROM @hr_ai_search_recovery_sql;
EXECUTE hr_ai_search_recovery_stmt;
DEALLOCATE PREPARE hr_ai_search_recovery_stmt;

CREATE TABLE IF NOT EXISTS `reporting_search_task_recovery`
(
    id                   CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '新恢复任务 ID',
    original_task_id     CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原失败任务 ID',
    outbox_event_id      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '恢复任务使用的持久事件 ID',
    reason               VARCHAR(500) NOT NULL COMMENT '管理员填写的恢复原因',
    requested_by_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际创建恢复的管理员 ID',
    `ext`                TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`             VARCHAR(255) NULL COMMENT '备注',
    `create_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`        VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`          CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`        VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`            INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    UNIQUE KEY uk_reporting_search_recovery_original(original_task_id),
    INDEX ix_reporting_search_recovery_event(outbox_event_id),
    CONSTRAINT fk_reporting_search_recovery_new FOREIGN KEY(id) REFERENCES reporting_async_task(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reporting_search_recovery_original FOREIGN KEY(original_task_id) REFERENCES reporting_async_task(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reporting_search_recovery_event FOREIGN KEY(outbox_event_id) REFERENCES reporting_outbox_event(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索任务人工恢复关联';

SET @hr_ai_search_recovery_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='reporting_search_task_recovery' AND (
     (COLUMN_NAME='id' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='original_task_id' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='outbox_event_id' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='reason' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=500 AND CHARACTER_SET_NAME='utf8mb4')
     OR (COLUMN_NAME='requested_by_user_id' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='ext' AND DATA_TYPE='text' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='remark' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=255)
     OR (COLUMN_NAME='create_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='create_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=64)
     OR (COLUMN_NAME='update_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='update_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='' AND CHARACTER_MAXIMUM_LENGTH=64)
     OR (COLUMN_NAME='create_time' AND COLUMN_TYPE='timestamp(6)' AND IS_NULLABLE='NO' AND UPPER(COLUMN_DEFAULT)='CURRENT_TIMESTAMP(6)' AND EXTRA='DEFAULT_GENERATED')
     OR (COLUMN_NAME='update_time' AND COLUMN_TYPE='timestamp(6)' AND IS_NULLABLE='NO' AND UPPER(COLUMN_DEFAULT)='CURRENT_TIMESTAMP(6)' AND EXTRA='DEFAULT_GENERATED on update CURRENT_TIMESTAMP(6)')
     OR (COLUMN_NAME='version' AND COLUMN_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0' AND EXTRA='')))=14
    AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_search_task_recovery' AND INDEX_NAME='PRIMARY')='id'
    AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_search_task_recovery'
         AND INDEX_NAME='uk_reporting_search_recovery_original' AND NON_UNIQUE=0)='original_task_id'
    AND (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r
         ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.TABLE_NAME=k.TABLE_NAME AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME
         WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME='reporting_search_task_recovery'
         AND k.REFERENCED_TABLE_SCHEMA=DATABASE() AND k.REFERENCED_COLUMN_NAME='id' AND r.DELETE_RULE='RESTRICT'
         AND ((k.COLUMN_NAME IN ('id','original_task_id') AND k.REFERENCED_TABLE_NAME='reporting_async_task')
              OR (k.COLUMN_NAME='outbox_event_id' AND k.REFERENCED_TABLE_NAME='reporting_outbox_event')))=3
    AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME='reporting_search_task_recovery' AND ENGINE='InnoDB')=1,
    'DO 0', 'SELECT `existing reporting_search_task_recovery has an incompatible definition`');
PREPARE hr_ai_search_recovery_stmt FROM @hr_ai_search_recovery_sql;
EXECUTE hr_ai_search_recovery_stmt;
DEALLOCATE PREPARE hr_ai_search_recovery_stmt;
SET @hr_ai_search_recovery_sql = NULL;
SHOW CREATE TABLE reporting_search_task_recovery;
