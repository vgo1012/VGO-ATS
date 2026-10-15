-- 版本：20260908_05；为持久工作任务增加同事务 Outbox 通知信封。
-- 前提：MySQL 8；已选择业务库，reporting_async_task 为 InnoDB 且 id 是 CHAR(26) ASCII 主键。
-- 数据影响：新增通知表、可空重试时间及领取索引；既有事实与任务值不改写，旧任务保持立即可领。
-- 历史搜索数据仍需受控重建；新字段为空不会自动重试既有 FAILED 任务。
-- 应先完成升级再启动新版写入器；升级期间停止旧写入器，避免继续产生没有投递意图的新事实。
-- 每步检查选定库元数据；已完成的原子 DDL 跳过，重跑补齐中断步骤。
-- 既有同名表不覆盖，执行后校验必需字段与外键；不兼容时失败，保留全部已有数据供人工核对。
-- 回退应用时保留此表及共享任务；恢复新版后继续领取。清理历史信封必须先确认工作和发布任务均结束，不能先删被引用任务。
SET NAMES utf8mb4;

SET @hr_ai_upgrade_20260908_05_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_async_task' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_async_task' AND COLUMN_NAME='id' AND DATA_TYPE='char'
       AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin'
       AND COLUMN_KEY='PRI')=1,
    'DO 0',
    'SELECT `transactional task outbox requires the existing InnoDB task primary key`'
);
PREPARE hr_ai_upgrade_20260908_05_stmt FROM @hr_ai_upgrade_20260908_05_sql;
EXECUTE hr_ai_upgrade_20260908_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_05_stmt;

SET @hr_ai_upgrade_20260908_05_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_async_task' AND COLUMN_NAME='next_retry_at')=0,
    'ALTER TABLE reporting_async_task ADD COLUMN next_retry_at TIMESTAMP(6) NULL COMMENT ''延迟重试最早领取时间，空值表示立即可领''',
    'DO 0'
);
PREPARE hr_ai_upgrade_20260908_05_stmt FROM @hr_ai_upgrade_20260908_05_sql;
EXECUTE hr_ai_upgrade_20260908_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_05_stmt;

SET @hr_ai_upgrade_20260908_05_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_async_task' AND COLUMN_NAME='next_retry_at' AND DATA_TYPE='timestamp'
       AND DATETIME_PRECISION=6 AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='')=1,
    'DO 0',
    'SELECT `existing next_retry_at definition is incompatible and has been preserved`'
);
PREPARE hr_ai_upgrade_20260908_05_stmt FROM @hr_ai_upgrade_20260908_05_sql;
EXECUTE hr_ai_upgrade_20260908_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_05_stmt;

SET @hr_ai_upgrade_20260908_05_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_async_task' AND INDEX_NAME='ix_reporting_task_retry')=0,
    'ALTER TABLE reporting_async_task ADD INDEX ix_reporting_task_retry (task_type,status,next_retry_at,create_time,id)',
    'DO 0'
);
PREPARE hr_ai_upgrade_20260908_05_stmt FROM @hr_ai_upgrade_20260908_05_sql;
EXECUTE hr_ai_upgrade_20260908_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_05_stmt;

SET @hr_ai_upgrade_20260908_05_sql = IF(
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_async_task' AND INDEX_NAME='ix_reporting_task_retry'
       AND NON_UNIQUE=1 AND SUB_PART IS NULL AND IS_VISIBLE='YES' AND COLLATION='A')
       ='task_type,status,next_retry_at,create_time,id',
    'DO 0',
    'SELECT `existing retry index definition is incompatible and has been preserved`'
);
PREPARE hr_ai_upgrade_20260908_05_stmt FROM @hr_ai_upgrade_20260908_05_sql;
EXECUTE hr_ai_upgrade_20260908_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_05_stmt;

CREATE TABLE IF NOT EXISTS `reporting_outbox_event`
(
    id                  CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '事件及独立发布任务 ID',
    work_task_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被通知的持久工作任务 ID',
    work_task_type      VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工作任务类型及消息路由键',
    event_type          VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件协议类型',
    event_version       INT NOT NULL COMMENT '事件协议版本',
    aggregate_type      VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务聚合类型',
    aggregate_id        CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务聚合 ID',
    occurred_at         TIMESTAMP(6) NOT NULL COMMENT '原始业务事件发生时间',
    trace_id            VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '安全链路关联 ID',
    actor_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原始操作者用户 ID 快照',
    owner_user_id       CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建时任务所属用户 ID 快照',
    owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建时任务所属部门 ID 快照',
    `ext`               TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`            VARCHAR(255) NULL COMMENT '备注',
    `create_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`       VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`         CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`       VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version`           INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    CONSTRAINT uk_reporting_outbox_work UNIQUE (work_task_id),
    CONSTRAINT fk_reporting_outbox_publication FOREIGN KEY (id) REFERENCES reporting_async_task (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reporting_outbox_work FOREIGN KEY (work_task_id) REFERENCES reporting_async_task (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reporting_outbox_protocol CHECK (event_version > 0),
    CONSTRAINT ck_reporting_outbox_distinct_tasks CHECK (id <> work_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='与业务事实同事务提交的持久任务通知信封';

SET @hr_ai_upgrade_20260908_05_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_outbox_event' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_outbox_event' AND COLUMN_NAME IN
       ('id','work_task_id','work_task_type','event_type','event_version','aggregate_type','aggregate_id',
        'occurred_at','trace_id','actor_user_id','owner_user_id','owner_department_id',
        'ext','remark','create_id','create_name','create_time','update_id','update_name','update_time','version'))=21 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_outbox_event' AND (
         (COLUMN_NAME IN ('id','work_task_id','aggregate_id','actor_user_id','owner_user_id','owner_department_id')
           AND COLUMN_TYPE='char(26)' AND IS_NULLABLE='NO' AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
         (COLUMN_NAME IN ('create_id','update_id') AND COLUMN_TYPE='char(26)' AND IS_NULLABLE='YES'
           AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
         (COLUMN_NAME IN ('work_task_type','event_type') AND COLUMN_TYPE='varchar(80)' AND IS_NULLABLE='NO'
           AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
         (COLUMN_NAME='aggregate_type' AND COLUMN_TYPE='varchar(60)' AND IS_NULLABLE='NO'
           AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
         (COLUMN_NAME='trace_id' AND COLUMN_TYPE='varchar(64)' AND IS_NULLABLE='NO'
           AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin') OR
         (COLUMN_NAME IN ('event_version','version') AND COLUMN_TYPE='int' AND IS_NULLABLE='NO') OR
         (COLUMN_NAME IN ('occurred_at','create_time','update_time') AND COLUMN_TYPE='timestamp(6)' AND IS_NULLABLE='NO') OR
         (COLUMN_NAME='ext' AND COLUMN_TYPE='text' AND IS_NULLABLE='YES') OR
         (COLUMN_NAME='remark' AND COLUMN_TYPE='varchar(255)' AND IS_NULLABLE='YES') OR
         (COLUMN_NAME IN ('create_name','update_name') AND COLUMN_TYPE='varchar(64)' AND IS_NULLABLE='YES')
       ))=21 AND
    (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_outbox_event' AND REFERENCED_TABLE_NAME='reporting_async_task' AND DELETE_RULE='RESTRICT')=2 AND
    (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_outbox_event' AND REFERENCED_TABLE_SCHEMA=DATABASE()
       AND REFERENCED_TABLE_NAME='reporting_async_task' AND REFERENCED_COLUMN_NAME='id'
       AND COLUMN_NAME IN ('id','work_task_id'))=2 AND
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_outbox_event' AND INDEX_NAME='PRIMARY')='id' AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_outbox_event' AND INDEX_NAME='uk_reporting_outbox_work'
       AND COLUMN_NAME='work_task_id' AND NON_UNIQUE=0)=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
       AND TABLE_NAME='reporting_outbox_event' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'
       AND CONSTRAINT_NAME IN ('ck_reporting_outbox_protocol','ck_reporting_outbox_distinct_tasks'))=2,
    'DO 0',
    'SELECT `existing reporting_outbox_event definition is incompatible and has been preserved`'
);
PREPARE hr_ai_upgrade_20260908_05_stmt FROM @hr_ai_upgrade_20260908_05_sql;
EXECUTE hr_ai_upgrade_20260908_05_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_05_stmt;
SET @hr_ai_upgrade_20260908_05_sql = NULL;
