-- 版本：20260908_01；用途：对齐面评接口已有的意见 1000 字、外部反馈来源 500 字上限。
-- 前提：MySQL 8，已存在 interview_feedback_version 及两个可空、无默认值的 utf8mb4 VARCHAR 列。
-- 执行前选择目标数据库，停止旧版应用及 Worker；在同一连接中顺序执行整个文件。
-- 先检查两个字段的前提，结构缺失或不符时停止；不自动替换非标准字段类型。
-- 仅扩容，不缩窄已更宽的字段，不改历史值；保留原字符集、排序规则及可空语义。
-- 每列独立判断，支持部分完成后接续与重复执行；DDL 隐式提交，不依赖事务回滚。
-- 全量建库使用 hr_ai_complete_schema.sql；本文件不写 Flyway 历史。
SET NAMES utf8mb4;

-- MySQL 的 PREPARE 不支持 SIGNAL；前提不符时读取带诊断名称的不存在列，使脚本在 DDL 前报错。
SET @hr_ai_upgrade_20260908_01_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_feedback_version'
       AND COLUMN_NAME IN ('short_comment', 'external_feedback_source')
       AND DATA_TYPE = 'varchar' AND CHARACTER_SET_NAME = 'utf8mb4'
       AND IS_NULLABLE = 'YES' AND COLUMN_DEFAULT IS NULL AND EXTRA = '') = 2,
    'DO 0',
    'SELECT `interview_feedback_version requires nullable utf8mb4 VARCHAR columns without defaults`'
);
PREPARE hr_ai_upgrade_20260908_01_stmt FROM @hr_ai_upgrade_20260908_01_sql;
EXECUTE hr_ai_upgrade_20260908_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_01_stmt;

SELECT IF(CHARACTER_MAXIMUM_LENGTH >= 1000, 'DO 0',
    CONCAT('ALTER TABLE interview_feedback_version MODIFY COLUMN short_comment VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE ',
           COLLATION_NAME, ' NULL COMMENT ',
           IF(FIND_IN_SET('NO_BACKSLASH_ESCAPES', @@SESSION.sql_mode),
              CONCAT(CHAR(39), REPLACE(COLUMN_COMMENT, CHAR(39), CONCAT(CHAR(39), CHAR(39))), CHAR(39)),
              QUOTE(COLUMN_COMMENT))))
INTO @hr_ai_upgrade_20260908_01_sql
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_feedback_version' AND COLUMN_NAME = 'short_comment';
PREPARE hr_ai_upgrade_20260908_01_stmt FROM @hr_ai_upgrade_20260908_01_sql;
EXECUTE hr_ai_upgrade_20260908_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_01_stmt;

SELECT IF(CHARACTER_MAXIMUM_LENGTH >= 500, 'DO 0',
    CONCAT('ALTER TABLE interview_feedback_version MODIFY COLUMN external_feedback_source VARCHAR(500) CHARACTER SET utf8mb4 COLLATE ',
           COLLATION_NAME, ' NULL COMMENT ',
           IF(FIND_IN_SET('NO_BACKSLASH_ESCAPES', @@SESSION.sql_mode),
              CONCAT(CHAR(39), REPLACE(COLUMN_COMMENT, CHAR(39), CONCAT(CHAR(39), CHAR(39))), CHAR(39)),
              QUOTE(COLUMN_COMMENT))))
INTO @hr_ai_upgrade_20260908_01_sql
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'interview_feedback_version' AND COLUMN_NAME = 'external_feedback_source';
PREPARE hr_ai_upgrade_20260908_01_stmt FROM @hr_ai_upgrade_20260908_01_sql;
EXECUTE hr_ai_upgrade_20260908_01_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260908_01_stmt;
SET @hr_ai_upgrade_20260908_01_sql = NULL;

SHOW COLUMNS FROM interview_feedback_version WHERE Field IN ('short_comment', 'external_feedback_source');
