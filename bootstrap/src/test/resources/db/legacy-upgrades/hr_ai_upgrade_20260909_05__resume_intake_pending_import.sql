-- 前置：MySQL 8，已选择目标业务库且 talent_resume_intake 已存在；执行前备份收件表。
-- 数据影响：只扩展状态约束以接受 PENDING_IMPORT，不回填或重置已有收件、候选人、简历版本或评分记录。
-- 幂等：状态约束已包含 PENDING_IMPORT 时跳过；缺失或旧约束在同一 ALTER 中替换。DDL 隐式提交，中断后可重跑。
-- 回退：先回退应用；不要删除 PENDING_IMPORT 记录。待这些记录已按业务完成处理后，才可基于备份另行评估收窄约束。
SET NAMES utf8mb4;

SET @hr_intake_pending_import_sql = IF(
  DATABASE() IS NOT NULL AND EXISTS (
    SELECT 1 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'talent_resume_intake'
  ),
  'DO 0',
  'SELECT `resume intake pending import upgrade requires selected database and talent_resume_intake table`'
);
PREPARE hr_intake_pending_import_stmt FROM @hr_intake_pending_import_sql;
EXECUTE hr_intake_pending_import_stmt;
DEALLOCATE PREPARE hr_intake_pending_import_stmt;

SET @hr_intake_pending_import_constraint_exists = EXISTS (
  SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'talent_resume_intake'
    AND CONSTRAINT_NAME = 'ck_talent_intake_status'
);
SET @hr_intake_pending_import_constraint_current = EXISTS (
  SELECT 1 FROM information_schema.CHECK_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'ck_talent_intake_status'
    AND CHECK_CLAUSE LIKE '%PENDING_IMPORT%'
);
SET @hr_intake_pending_import_sql = IF(
  @hr_intake_pending_import_constraint_current,
  'DO 0',
  CONCAT(
    'ALTER TABLE talent_resume_intake ',
    IF(@hr_intake_pending_import_constraint_exists, 'DROP CHECK ck_talent_intake_status, ', ''),
    'ADD CONSTRAINT ck_talent_intake_status CHECK (status IN (''PENDING_IMPORT'',''PARSING'',''IDENTITY_REVIEW_REQUIRED'',''COMPLETED'',''READY_FOR_CONFIRMATION'',''CONFIRMED'',''IGNORED'',''REJECTED'',''QUARANTINED'',''FAILED_RETRYABLE''))'
  )
);
PREPARE hr_intake_pending_import_stmt FROM @hr_intake_pending_import_sql;
EXECUTE hr_intake_pending_import_stmt;
DEALLOCATE PREPARE hr_intake_pending_import_stmt;

SELECT CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'ck_talent_intake_status';
