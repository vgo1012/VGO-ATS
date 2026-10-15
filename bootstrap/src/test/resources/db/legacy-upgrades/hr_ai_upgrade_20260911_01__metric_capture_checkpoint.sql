-- 版本：20260911_01；报表读取改为只读，后台分批投影使用独立检查点。
-- 前提：MySQL 8，选定现有业务库且 reporting_metric_event 已存在；停止旧应用及采集 Worker 后升级。
-- 数据影响：只新增水位表，不修改或清空任何历史指标、审计、业务来源与已有水位。
-- 同名表必须具有兼容字段、主键和 InnoDB 引擎，否则拒绝；新建表原子完成，重复或中断后重跑保留数据。
-- 回退旧应用时保留新表；恢复新版从既有水位继续，首次初始化期间报表返回可恢复状态。
SET NAMES utf8mb4;
SET @hr_metric_sql = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_metric_event')=1,
    'DO 0', 'SELECT `metric capture requires reporting_metric_event`');
PREPARE hr_metric_stmt FROM @hr_metric_sql;
EXECUTE hr_metric_stmt;
DEALLOCATE PREPARE hr_metric_stmt;

CREATE TABLE IF NOT EXISTS `reporting_metric_capture_checkpoint`
(
    `checkpoint_name` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT '来源与通道组成的检查点名称',
    `source_code` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '指标来源编码',
    `lane` VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'INCREMENTAL 或 RECONCILIATION',
    `last_source_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上次已扫描来源主键',
    `cycle_upper_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '本周期固定上界，空值表示周期已完成',
    `cycle_started_at` TIMESTAMP(6) NULL COMMENT '进行中周期起点',
    `last_completed_at` TIMESTAMP(6) NULL COMMENT '最近完整周期起点，首次未完成时为空',
    `version` INT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本号',
    `ext` TEXT NULL COMMENT 'JSON 扩展信息',
    `remark` VARCHAR(255) NULL COMMENT '备注',
    `create_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建人 ID',
    `create_name` VARCHAR(64) NULL COMMENT '创建人名称',
    `create_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `update_id` CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '更新人 ID',
    `update_name` VARCHAR(64) NULL COMMENT '更新人名称',
    `update_time` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报表指标独立增量及补偿水位';

SET @hr_metric_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='reporting_metric_capture_checkpoint' AND (
     (COLUMN_NAME='checkpoint_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=100 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='source_code' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=40 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='lane' AND DATA_TYPE='varchar' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=30 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='last_source_id' AND DATA_TYPE='char' AND IS_NULLABLE='NO' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='cycle_upper_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='cycle_started_at' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6 AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='last_completed_at' AND DATA_TYPE='timestamp' AND IS_NULLABLE='YES' AND DATETIME_PRECISION=6 AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='version' AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0' AND EXTRA='')
     OR (COLUMN_NAME='ext' AND DATA_TYPE='text' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='remark' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=255 AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='create_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='create_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64 AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='create_time' AND DATA_TYPE='timestamp' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6 AND UPPER(COLUMN_DEFAULT)='CURRENT_TIMESTAMP(6)' AND EXTRA='DEFAULT_GENERATED')
     OR (COLUMN_NAME='update_id' AND DATA_TYPE='char' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='update_name' AND DATA_TYPE='varchar' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=64 AND COLUMN_DEFAULT IS NULL AND EXTRA='')
     OR (COLUMN_NAME='update_time' AND DATA_TYPE='timestamp' AND IS_NULLABLE='NO' AND DATETIME_PRECISION=6 AND UPPER(COLUMN_DEFAULT)='CURRENT_TIMESTAMP(6)' AND EXTRA='DEFAULT_GENERATED on update CURRENT_TIMESTAMP(6)')))=16
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME='reporting_metric_capture_checkpoint')=16
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME='reporting_metric_capture_checkpoint' AND INDEX_NAME='PRIMARY')=1
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME='reporting_metric_capture_checkpoint' AND INDEX_NAME='PRIMARY'
         AND COLUMN_NAME='checkpoint_name' AND SEQ_IN_INDEX=1)=1
    AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME='reporting_metric_capture_checkpoint' AND ENGINE='InnoDB')=1,
    'DO 0', 'SELECT `existing reporting_metric_capture_checkpoint has an incompatible definition`');
PREPARE hr_metric_stmt FROM @hr_metric_sql;
EXECUTE hr_metric_stmt;
DEALLOCATE PREPARE hr_metric_stmt;
SET @hr_metric_sql = NULL;
SHOW CREATE TABLE reporting_metric_capture_checkpoint;
