-- 版本：20260910_02；用途：支持智能筛选退回我的简历并保留准入与评分历史。
-- 前提：MySQL 8，已执行 20260910_01 智能筛选准入升级，目标库已存在 matching_screening_admission。
-- 停止旧版应用及 Worker，选择目标数据库，在同一连接顺序执行整个文件。
-- 数据影响：仅新增可空 returned_at；已有准入均保持有效，不覆盖评分、技术版本或审计。
-- 进入过招聘流程的记录由应用查询实时排除（包括已终止流程），无需回填或删除历史。
-- 支持重复执行和 DDL 完成后中断恢复；DDL 隐式提交，执行前按部署规范备份。
SET NAMES utf8mb4;

SET @hr_ai_upgrade_20260910_02_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='matching_screening_admission'
       AND COLUMN_NAME IN ('id','resume_version_id','version','admitted_at'))=4,
    'DO 0',
    'SELECT `matching_screening_admission requires the 20260910_01 schema`'
);
PREPARE hr_ai_upgrade_20260910_02_stmt FROM @hr_ai_upgrade_20260910_02_sql;
EXECUTE hr_ai_upgrade_20260910_02_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260910_02_stmt;

SET @hr_ai_upgrade_20260910_02_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='matching_screening_admission' AND COLUMN_NAME='returned_at')=0,
    'ALTER TABLE matching_screening_admission ADD COLUMN returned_at TIMESTAMP(6) NULL COMMENT ''退回我的简历时间，空值表示仍在智能筛选'' AFTER admitted_at',
    IF((SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='matching_screening_admission' AND COLUMN_NAME='returned_at'
          AND DATA_TYPE='timestamp' AND DATETIME_PRECISION=6 AND IS_NULLABLE='YES'
          AND COLUMN_DEFAULT IS NULL AND EXTRA='')=1,
       'DO 0',
       'SELECT `existing matching_screening_admission.returned_at has an incompatible definition`')
);
PREPARE hr_ai_upgrade_20260910_02_stmt FROM @hr_ai_upgrade_20260910_02_sql;
EXECUTE hr_ai_upgrade_20260910_02_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260910_02_stmt;
SET @hr_ai_upgrade_20260910_02_sql = NULL;

SHOW COLUMNS FROM matching_screening_admission WHERE Field='returned_at';
