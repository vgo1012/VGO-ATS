-- 版本：20260919_01；在不可变 JD 版本中冻结工作区域，供历史招聘流程画像使用。
-- 前提：MySQL 8，已部署 demand_jd、demand_jd_version、demand_jd_code_option；升级前停止旧版 JD 写入并完成备份。
-- 数据影响：新增可空区域代码；仅为尚未冻结区域的历史版本复制其逻辑 JD 当前区域，不改 JD、流程或简历事实。
-- 重跑与部分完成：新增列和回填均先检查元数据或仅更新 NULL，重复执行不会覆盖已冻结区域。
-- 恢复：旧应用可忽略并保留该列；回退不会改变既有业务，删除列前必须备份且确认新版已停用。
SET NAMES utf8mb4;

SET @jd_version_region_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_version' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_code_option' AND ENGINE='InnoDB')=1,
    'DO 0',
    'SELECT `jd version work region upgrade requires demand_jd, demand_jd_version and demand_jd_code_option`'
);
PREPARE jd_version_region_stmt FROM @jd_version_region_sql;
EXECUTE jd_version_region_stmt;
DEALLOCATE PREPARE jd_version_region_stmt;

SET @jd_version_region_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_version' AND COLUMN_NAME='work_region_code')=0,
    'ALTER TABLE demand_jd_version ADD COLUMN work_region_code CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''版本冻结工作区域，历史无明确区域可空'' AFTER recruitment_type',
    'DO 0'
);
PREPARE jd_version_region_stmt FROM @jd_version_region_sql;
EXECUTE jd_version_region_stmt;
DEALLOCATE PREPARE jd_version_region_stmt;

SET @jd_version_region_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_version' AND COLUMN_NAME='work_region_code'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=3 AND IS_NULLABLE='YES'
     AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')=1,
    'UPDATE demand_jd_version v JOIN demand_jd j ON j.id=v.jd_id SET v.work_region_code=j.work_region_code WHERE v.work_region_code IS NULL AND j.work_region_code IS NOT NULL',
    'SELECT `existing demand_jd_version.work_region_code definition is incompatible and has been preserved`'
);
PREPARE jd_version_region_stmt FROM @jd_version_region_sql;
EXECUTE jd_version_region_stmt;
DEALLOCATE PREPARE jd_version_region_stmt;

SET @jd_version_region_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd_version' AND COLUMN_NAME='work_region_code'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=3 AND IS_NULLABLE='YES'
     AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin')=1 AND
    (SELECT COUNT(*) FROM demand_jd_version v LEFT JOIN demand_jd_code_option o
     ON o.kind='REGION' AND o.code=v.work_region_code
     WHERE v.work_region_code IS NOT NULL AND o.id IS NULL)=0,
    'DO 0',
    'SELECT `demand_jd_version.work_region_code contains unknown region codes`'
);
PREPARE jd_version_region_stmt FROM @jd_version_region_sql;
EXECUTE jd_version_region_stmt;
DEALLOCATE PREPARE jd_version_region_stmt;
SET @jd_version_region_sql = NULL;
