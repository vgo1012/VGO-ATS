-- 版本：20260924_01；为部门 JD 应用记录精确来源版本，并支持部门来源分页。
-- 前提：MySQL 8，已执行至 20260923_02；在已核对的目标库备份后停止旧版 JD 写入。
-- 数据影响：只增加可空列和索引；历史 JD 均保留空值，按原来源展示，不合并或改写编号。
-- 重跑与部分完成：每一步按当前库元数据检查，中断后可继续；已有业务数据及审核历史不变。
-- 恢复：旧版应用可忽略新增列；回退应用仍保留该列及其引用，勿删除来源版本。
SET NAMES utf8mb4;

SET @jd_source_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('demand_jd', 'demand_jd_version') AND ENGINE='InnoDB')=2 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND ((TABLE_NAME='demand_jd' AND COLUMN_NAME='id') OR
          (TABLE_NAME='demand_jd_version' AND COLUMN_NAME='id'))
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=26
     AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO')=2,
    'DO 0', 'SELECT `department JD source upgrade requires matching JD and version IDs`');
PREPARE jd_source_stmt FROM @jd_source_sql;
EXECUTE jd_source_stmt;
DEALLOCATE PREPARE jd_source_stmt;

SET @jd_source_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='applied_from_jd_version_id')=0,
    'ALTER TABLE demand_jd ADD COLUMN applied_from_jd_version_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''应用部门来源 JD 时冻结的来源版本 ID；空值表示来源 JD''',
    'DO 0');
PREPARE jd_source_stmt FROM @jd_source_sql;
EXECUTE jd_source_stmt;
DEALLOCATE PREPARE jd_source_stmt;

SET @jd_source_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='applied_from_jd_version_id'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=26
     AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES')=1,
    'DO 0', 'SELECT `applied_from_jd_version_id has an incompatible definition`');
PREPARE jd_source_stmt FROM @jd_source_sql;
EXECUTE jd_source_stmt;
DEALLOCATE PREPARE jd_source_stmt;

SET @jd_source_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_department_sources')=0,
    'ALTER TABLE demand_jd ADD INDEX ix_demand_jd_department_sources (owner_department_id, applied_from_jd_version_id, deleted, status, create_time DESC, id DESC)',
    'DO 0');
PREPARE jd_source_stmt FROM @jd_source_sql;
EXECUTE jd_source_stmt;
DEALLOCATE PREPARE jd_source_stmt;

SET @jd_source_sql = IF(
    (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
     FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_department_sources'
     AND NON_UNIQUE=1 AND SUB_PART IS NULL)=
     'owner_department_id,applied_from_jd_version_id,deleted,status,create_time,id',
    'DO 0', 'SELECT `department JD source index has an incompatible definition`');
PREPARE jd_source_stmt FROM @jd_source_sql;
EXECUTE jd_source_stmt;
DEALLOCATE PREPARE jd_source_stmt;

SET @jd_source_sql = NULL;
