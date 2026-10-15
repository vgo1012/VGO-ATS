-- 版本：20260907_04；修复旧库无法创建 AI 文本抽取和模型运行记录。
-- 前置：MySQL 8，已存在以下三张 AI 运行表；客户端选择目标数据库，停止应用/Worker 后执行。
-- 与当前完整基线一致：外部调用尚未发生时元数据为 NULL，成功后回填真实值，不伪造供应商/版本。
-- 只放宽五个元数据列的非空约束；不改写已有记录，不修改类型、索引、外键或 Flyway 历史。
-- 每列独立检查当前数据库；支持部分完成及重复执行。缺少前置表/列时 ALTER 明确失败。
SET NAMES utf8mb4;

SET @hr_ai_upgrade_20260907_04_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'talent_resume_extraction_run'
              AND COLUMN_NAME = 'extractor_version' AND IS_NULLABLE = 'YES'),
    'DO 0',
    'ALTER TABLE talent_resume_extraction_run MODIFY COLUMN extractor_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''抽取器版本，抽取完成后回填'''
);
PREPARE hr_ai_upgrade_20260907_04_stmt FROM @hr_ai_upgrade_20260907_04_sql;
EXECUTE hr_ai_upgrade_20260907_04_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_04_stmt;

SET @hr_ai_upgrade_20260907_04_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'talent_resume_ai_model_run'
              AND COLUMN_NAME = 'model_provider' AND IS_NULLABLE = 'YES'),
    'DO 0',
    'ALTER TABLE talent_resume_ai_model_run MODIFY COLUMN model_provider VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''模型服务提供方，模型调用后回填'''
);
PREPARE hr_ai_upgrade_20260907_04_stmt FROM @hr_ai_upgrade_20260907_04_sql;
EXECUTE hr_ai_upgrade_20260907_04_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_04_stmt;

SET @hr_ai_upgrade_20260907_04_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'talent_resume_ai_model_run'
              AND COLUMN_NAME = 'model_version' AND IS_NULLABLE = 'YES'),
    'DO 0',
    'ALTER TABLE talent_resume_ai_model_run MODIFY COLUMN model_version VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''模型版本标识，模型调用后回填'''
);
PREPARE hr_ai_upgrade_20260907_04_stmt FROM @hr_ai_upgrade_20260907_04_sql;
EXECUTE hr_ai_upgrade_20260907_04_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_04_stmt;

SET @hr_ai_upgrade_20260907_04_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd_ai_model_run'
              AND COLUMN_NAME = 'model_provider' AND IS_NULLABLE = 'YES'),
    'DO 0',
    'ALTER TABLE demand_jd_ai_model_run MODIFY COLUMN model_provider VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''模型服务提供方，模型调用后回填'''
);
PREPARE hr_ai_upgrade_20260907_04_stmt FROM @hr_ai_upgrade_20260907_04_sql;
EXECUTE hr_ai_upgrade_20260907_04_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_04_stmt;

SET @hr_ai_upgrade_20260907_04_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand_jd_ai_model_run'
              AND COLUMN_NAME = 'model_version' AND IS_NULLABLE = 'YES'),
    'DO 0',
    'ALTER TABLE demand_jd_ai_model_run MODIFY COLUMN model_version VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''模型版本标识，模型调用后回填'''
);
PREPARE hr_ai_upgrade_20260907_04_stmt FROM @hr_ai_upgrade_20260907_04_sql;
EXECUTE hr_ai_upgrade_20260907_04_stmt;
DEALLOCATE PREPARE hr_ai_upgrade_20260907_04_stmt;
SET @hr_ai_upgrade_20260907_04_sql = NULL;