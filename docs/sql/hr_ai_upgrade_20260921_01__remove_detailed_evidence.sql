-- 前置条件：已部署支持无明细证据表的 2026-09-21 应用版本，并已停止旧版本写入。
-- 数据影响：永久删除简历逐字段原文摘录与匹配逐项证据路径；历史 RESUME_EVIDENCE 更正来源统一为 HUMAN_PROVIDED。原始简历、结构化事实、评分结果、AI 总结和运行元数据不受影响。
-- 执行前备份：如需保留历史审计数据，先使用 mysqldump 单独备份 talent_resume_fact_evidence、matching_evaluation_evidence。
-- 恢复方式：从备份重建并回灌两张表，将需要恢复的更正明细来源改回 RESUME_EVIDENCE，同时回滚到旧应用版本。
-- 可重入性：所有删除、数据收敛与约束调整均基于元数据或 IF EXISTS，支持重复执行和部分完成状态。

DROP TABLE IF EXISTS `matching_evaluation_evidence`;
DROP TABLE IF EXISTS `talent_resume_fact_evidence`;

SET @correction_table_exists = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_fact_correction_change');
SET @correction_source_sql = IF(@correction_table_exists=1,
  'UPDATE talent_fact_correction_change SET source_type=''HUMAN_PROVIDED'' WHERE source_type=''RESUME_EVIDENCE''',
  'DO 0');
PREPARE correction_source_stmt FROM @correction_source_sql;
EXECUTE correction_source_stmt;
DEALLOCATE PREPARE correction_source_stmt;

SET @correction_source_sql = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='talent_fact_correction_change'
    AND CONSTRAINT_NAME='ck_talent_correction_source' AND CONSTRAINT_TYPE='CHECK')=1,
  'ALTER TABLE talent_fact_correction_change DROP CHECK ck_talent_correction_source',
  'DO 0');
PREPARE correction_source_stmt FROM @correction_source_sql;
EXECUTE correction_source_stmt;
DEALLOCATE PREPARE correction_source_stmt;

SET @correction_source_sql = IF(@correction_table_exists=1 AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='talent_fact_correction_change'
    AND CONSTRAINT_NAME='ck_talent_correction_source')=0,
  'ALTER TABLE talent_fact_correction_change ADD CONSTRAINT ck_talent_correction_source CHECK (source_type = ''HUMAN_PROVIDED'')',
  'DO 0');
PREPARE correction_source_stmt FROM @correction_source_sql;
EXECUTE correction_source_stmt;
DEALLOCATE PREPARE correction_source_stmt;
