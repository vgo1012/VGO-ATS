-- 前提：MySQL 8；已执行至 V2026100803，选择目标库并备份 demand_jd，暂停旧版 JD 写入。
-- 数据影响：新增列默认 FALSE，历史 JD 不主动共享；不更新 public_visible 或任何历史业务行。
-- 重跑与部分完成：新增前检查当前库元数据；同名不兼容对象报错，不自动替换。
-- 恢复：旧版应用可忽略新增列与索引；回退应用时保留已写入的共享选择。
SET NAMES utf8mb4;

SET @public_jd_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME IN
         ('id', 'public_visible', 'deleted', 'status', 'create_time'))=5,
    'DO 0', 'SELECT `public JD content upgrade requires an InnoDB demand_jd with required columns`');
PREPARE public_jd_stmt FROM @public_jd_sql;
EXECUTE public_jd_stmt;
DEALLOCATE PREPARE public_jd_stmt;

SET @public_jd_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='content_shared')=0 OR
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='content_shared'
     AND DATA_TYPE='tinyint' AND COLUMN_TYPE='tinyint(1)' AND IS_NULLABLE='NO'
     AND COLUMN_DEFAULT='0' AND EXTRA='' AND COLUMN_COMMENT='主动共享 JD 内容，不授予招聘记录权限')=1,
    'DO 0', 'SELECT `demand_jd.content_shared has an incompatible definition`');
PREPARE public_jd_stmt FROM @public_jd_sql;
EXECUTE public_jd_stmt;
DEALLOCATE PREPARE public_jd_stmt;

SET @public_jd_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_public_sources')=0 OR
    ((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_public_sources')=5 AND
     (SELECT GROUP_CONCAT(CONCAT(SEQ_IN_INDEX, ':', COLUMN_NAME, ':', COLLATION)
             ORDER BY SEQ_IN_INDEX SEPARATOR ',')
      FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_public_sources'
      AND NON_UNIQUE=1 AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL)=
      '1:content_shared:A,2:deleted:A,3:status:A,4:create_time:D,5:id:D'),
    'DO 0', 'SELECT `ix_demand_jd_public_sources has an incompatible definition`');
PREPARE public_jd_stmt FROM @public_jd_sql;
EXECUTE public_jd_stmt;
DEALLOCATE PREPARE public_jd_stmt;

SET @public_jd_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='content_shared')=0,
    'ALTER TABLE demand_jd ADD COLUMN content_shared BOOLEAN NOT NULL DEFAULT FALSE COMMENT ''主动共享 JD 内容，不授予招聘记录权限'' AFTER public_visible',
    'DO 0');
PREPARE public_jd_stmt FROM @public_jd_sql;
EXECUTE public_jd_stmt;
DEALLOCATE PREPARE public_jd_stmt;

SET @public_jd_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_public_sources')=0,
    'ALTER TABLE demand_jd ADD INDEX ix_demand_jd_public_sources (content_shared, deleted, status, create_time DESC, id DESC)',
    'DO 0');
PREPARE public_jd_stmt FROM @public_jd_sql;
EXECUTE public_jd_stmt;
DEALLOCATE PREPARE public_jd_stmt;

SET @public_jd_sql = NULL;
