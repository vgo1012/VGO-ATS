-- 版本：20260908_11；为候选人列表的更新时间、ID 倒序游标提供有序索引。
-- 前提：MySQL 8，先选择目标数据库；talent_candidate 已有 update_time 与 id。
-- 数据影响：仅新增非唯一索引，不修改候选人、归属、权限或历史数据。
-- 部署：先停止旧版应用及 Worker，备份后在同一连接完整执行；大表需预留索引构建空间和维护窗口。
-- 恢复：DDL 隐式提交，中断后重跑；同名兼容索引跳过，不兼容定义拒绝，不自动删除已有索引。
-- 回退：本索引无业务唯一性或外键职责，确认无查询提示依赖后可人工删除 ix_talent_candidate_page；不回退任何业务数据。
SET NAMES utf8mb4;

SET @hr_ai_candidate_page_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_candidate'
       AND COLUMN_NAME IN ('id','update_time'))=2,
    'DO 0',
    'SELECT `candidate page index requires talent_candidate.id and update_time in the selected database`');
PREPARE hr_ai_candidate_page_stmt FROM @hr_ai_candidate_page_sql;
EXECUTE hr_ai_candidate_page_stmt;
DEALLOCATE PREPARE hr_ai_candidate_page_stmt;

SET @hr_ai_candidate_page_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_candidate'
       AND INDEX_NAME='ix_talent_candidate_page')=0,
    'ALTER TABLE talent_candidate ADD INDEX ix_talent_candidate_page (update_time, id)',
    IF((SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_candidate'
          AND INDEX_NAME='ix_talent_candidate_page')=2
       AND (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='talent_candidate'
          AND INDEX_NAME='ix_talent_candidate_page' AND NON_UNIQUE=1
          AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL
          AND COLLATION='A' AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='update_time')
                                OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='id')))=2,
       'DO 0',
       'SELECT `existing candidate page index has an incompatible definition`'));
PREPARE hr_ai_candidate_page_stmt FROM @hr_ai_candidate_page_sql;
EXECUTE hr_ai_candidate_page_stmt;
DEALLOCATE PREPARE hr_ai_candidate_page_stmt;
SET @hr_ai_candidate_page_sql = NULL;
