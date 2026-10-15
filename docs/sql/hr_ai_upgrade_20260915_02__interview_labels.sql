-- 版本：20260915_02；独立面试标签与排期面评。
-- 前提：MySQL 8，选中当前收敛数据库且 interview_interview 已存在；先备份面试表并停止旧应用写入。
-- 数据影响：新增可空面评列；EXTERNAL 为普通外部展示标签，既有 INTERNAL/EXTERNAL_CLIENT/EXTERNAL_CLIENT_AI 值原样保留。
-- 部分完成及重跑：检查所选数据库的列和 CHECK 元数据，已完成的步骤不重复执行。
-- 恢复：保留新增列与面评；存在 EXTERNAL 数据后不可直接回退到不识别该标签的旧应用。
-- 约束变更先扩展允许值再启用新版；若中断在删除 CHECK 后，重新执行会恢复新 CHECK，禁止在该窗口开放写入。
SET NAMES utf8mb4;
SET @interview_label_sql = IF(DATABASE() IS NOT NULL AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='interview_interview')=1,
 'DO 0', 'SELECT `interview label upgrade requires interview_interview in selected database`');
PREPARE interview_label_stmt FROM @interview_label_sql;
EXECUTE interview_label_stmt;
DEALLOCATE PREPARE interview_label_stmt;

SET @interview_label_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='interview_interview' AND COLUMN_NAME='initial_comment')=0,
 'ALTER TABLE interview_interview ADD COLUMN initial_comment VARCHAR(1000) NULL COMMENT ''排期时填写的面评文字，不代表正式面试结果''', 'DO 0');
PREPARE interview_label_stmt FROM @interview_label_sql;
EXECUTE interview_label_stmt;
DEALLOCATE PREPARE interview_label_stmt;

SET @interview_label_sql = IF((SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='ck_interview_type' AND CHECK_CLAUSE NOT LIKE '%''EXTERNAL''%')>0,
 'ALTER TABLE interview_interview DROP CHECK ck_interview_type', 'DO 0');
PREPARE interview_label_stmt FROM @interview_label_sql;
EXECUTE interview_label_stmt;
DEALLOCATE PREPARE interview_label_stmt;

SET @interview_label_sql = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='interview_interview' AND CONSTRAINT_NAME='ck_interview_type')=0,
 'ALTER TABLE interview_interview ADD CONSTRAINT ck_interview_type CHECK (interview_type IN (''INTERNAL'',''EXTERNAL'',''EXTERNAL_CLIENT'',''EXTERNAL_CLIENT_AI''))', 'DO 0');
PREPARE interview_label_stmt FROM @interview_label_sql;
EXECUTE interview_label_stmt;
DEALLOCATE PREPARE interview_label_stmt;
SET @interview_label_sql = NULL;
