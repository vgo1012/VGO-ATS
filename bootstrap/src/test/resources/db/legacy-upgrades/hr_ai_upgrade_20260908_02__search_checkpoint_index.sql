-- 版本：20260908_02；用途：将增量投影检查点绑定到实际搜索索引，避免全量切换遗漏增量事实。
-- 前提：MySQL 8；已存在 search_projection_checkpoint，包含 projection_name / version / last_created_at / last_snapshot_id。
-- 执行前选择目标数据库并停止旧版搜索 Worker；同一连接顺序执行整个文件。
-- 仅新增可空列，不修改历史游标、统计或审计字段。旧行值为 NULL，新 Worker 首次运行时以版本 CAS 绑定实际索引并从头按页重新投影。
-- 如果列已存在则校验定义并跳过，支持重复执行和 DDL 完成后中断的恢复；不删除任何索引或业务事实。
-- 完整建库使用 hr_ai_complete_schema.sql；本文件不写 Flyway 历史。DDL 隐式提交，升级前按部署规范备份。
SET NAMES utf8mb4;

SET @hr_ai_upgrade_20260908_02_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_projection_checkpoint'
       AND COLUMN_NAME IN ('projection_name','version','last_created_at','last_snapshot_id'))=4,
    'DO 0',
    'SELECT `search_projection_checkpoint requires its existing cursor and version columns`'
);
PREPARE hr_ai_upgrade_20260908_02_stmt FROM @hr_ai_upgrade_20260908_02_sql;
EXECUTE hr_ai_upgrade_20260908_02_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_02_stmt;

SET @hr_ai_upgrade_20260908_02_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_projection_checkpoint' AND COLUMN_NAME='index_name')=0,
    'ALTER TABLE search_projection_checkpoint ADD COLUMN index_name VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''当前游标对应的实际搜索索引，空值需要重新绑定并投影'' AFTER projection_name',
    IF((SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='search_projection_checkpoint' AND COLUMN_NAME='index_name'
          AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH>=120 AND CHARACTER_SET_NAME='ascii'
          AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL AND EXTRA='')=1,
       'DO 0',
       'SELECT `existing search_projection_checkpoint.index_name has an incompatible definition`')
);
PREPARE hr_ai_upgrade_20260908_02_stmt FROM @hr_ai_upgrade_20260908_02_sql;
EXECUTE hr_ai_upgrade_20260908_02_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_02_stmt;
SET @hr_ai_upgrade_20260908_02_sql = NULL;

SHOW COLUMNS FROM search_projection_checkpoint WHERE Field='index_name';
