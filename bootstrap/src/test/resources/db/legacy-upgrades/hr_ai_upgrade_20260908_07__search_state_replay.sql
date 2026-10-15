-- 版本：20260908_07；索引切换后持久回放候选人当前状态。
-- 前提：MySQL 8，已选定业务库且 talent_candidate 和 search_projection_checkpoint 已存在；停止旧版搜索 Worker。
-- 数据影响：只新增独立回放进度表，不改候选人、事实、旧时间游标或历史失败任务。
-- CREATE TABLE IF NOT EXISTS 在选定库中检查元数据；原子 DDL 完成后中断可重跑，已保存游标保持不变。
-- 已有同名表必须兼容关键字段、审计字段和主键，否则报错停止；不自动修剪或覆盖历史数据。
-- 回退应用时保留新表；恢复新版后继续回放。全量空库使用 hr_ai_complete_schema.sql；不修改 Flyway 历史。
SET NAMES utf8mb4;

SET @hr_ai_state_replay_sql = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('talent_candidate','search_projection_checkpoint'))=2,
    'DO 0', 'SELECT `search state replay requires candidate and projection tables`');
PREPARE hr_ai_state_replay_stmt FROM @hr_ai_state_replay_sql;
EXECUTE hr_ai_state_replay_stmt;
DEALLOCATE PREPARE hr_ai_state_replay_stmt;

CREATE TABLE IF NOT EXISTS `search_state_replay_checkpoint`
(
    projection_name  VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '搜索投影名称',
    index_name       VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前游标对应的实际搜索索引，空值需要重新绑定并投影',
    last_candidate_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近已回放候选人 ID',
    completed       BOOLEAN NOT NULL DEFAULT FALSE COMMENT '当前物理索引的完整状态回放是否完成',
    projected_count  BIGINT                                         NOT NULL DEFAULT 0 COMMENT '已成功回放候选人数量',
    `version`        INT                                            NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext`            TEXT NULL COMMENT 'JSON 扩展信息',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name`    VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id`      CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name`    VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time`    TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='人才搜索当前状态回放进度';

SET @hr_ai_state_replay_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='search_state_replay_checkpoint' AND (
     (COLUMN_NAME='projection_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND EXTRA='' AND COLUMN_DEFAULT IS NULL AND CHARACTER_MAXIMUM_LENGTH=80 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='index_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND EXTRA='' AND COLUMN_DEFAULT IS NULL AND CHARACTER_MAXIMUM_LENGTH=120 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='last_candidate_id' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND EXTRA='' AND COLUMN_DEFAULT IS NULL AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='completed' AND DATA_TYPE='tinyint' AND IS_NULLABLE='NO' AND EXTRA='' AND UPPER(COLUMN_DEFAULT)=UPPER('0'))
     OR (COLUMN_NAME='projected_count' AND DATA_TYPE='bigint' AND IS_NULLABLE='NO' AND EXTRA='' AND UPPER(COLUMN_DEFAULT)=UPPER('0'))
     OR (COLUMN_NAME='version' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND EXTRA='' AND UPPER(COLUMN_DEFAULT)=UPPER('0'))
     OR (COLUMN_NAME='ext' AND DATA_TYPE='text' AND IS_NULLABLE='YES' AND EXTRA='' AND COLUMN_DEFAULT IS NULL)
     OR (COLUMN_NAME='remark' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND EXTRA='' AND COLUMN_DEFAULT IS NULL AND CHARACTER_MAXIMUM_LENGTH=255)
     OR (COLUMN_NAME='create_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND EXTRA='' AND COLUMN_DEFAULT IS NULL AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='create_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND EXTRA='' AND COLUMN_DEFAULT IS NULL AND CHARACTER_MAXIMUM_LENGTH=64)
     OR (COLUMN_NAME='update_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND EXTRA='' AND COLUMN_DEFAULT IS NULL AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')
     OR (COLUMN_NAME='update_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND EXTRA='' AND COLUMN_DEFAULT IS NULL AND CHARACTER_MAXIMUM_LENGTH=64)
     OR (COLUMN_NAME='create_time' AND DATA_TYPE='timestamp' AND IS_NULLABLE='NO' AND EXTRA='DEFAULT_GENERATED' AND UPPER(COLUMN_DEFAULT)=UPPER('CURRENT_TIMESTAMP(6)') AND DATETIME_PRECISION=6)
     OR (COLUMN_NAME='update_time' AND DATA_TYPE='timestamp' AND IS_NULLABLE='NO' AND EXTRA='DEFAULT_GENERATED on update CURRENT_TIMESTAMP(6)' AND UPPER(COLUMN_DEFAULT)=UPPER('CURRENT_TIMESTAMP(6)') AND DATETIME_PRECISION=6)))=14
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME='search_state_replay_checkpoint' AND INDEX_NAME='PRIMARY')=1
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME='search_state_replay_checkpoint' AND INDEX_NAME='PRIMARY'
         AND COLUMN_NAME='projection_name' AND SEQ_IN_INDEX=1)=1
    AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME='search_state_replay_checkpoint' AND ENGINE='InnoDB')=1,
    'DO 0', 'SELECT `existing search_state_replay_checkpoint has an incompatible definition`');
PREPARE hr_ai_state_replay_stmt FROM @hr_ai_state_replay_sql;
EXECUTE hr_ai_state_replay_stmt;
DEALLOCATE PREPARE hr_ai_state_replay_stmt;
SET @hr_ai_state_replay_sql = NULL;
SHOW CREATE TABLE search_state_replay_checkpoint;
