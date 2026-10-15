-- 前置条件：MySQL 8，已选择部署库，已部署包含以下四表的完整基线/历史升级。
-- 数据影响：只增加 candidate_id 首列普通索引，不修改、迁移或删除任何业务数据。
-- 发布步骤：先备份并确认目标库，安排维护窗口；逐条 DDL 隐式提交，支持部分完成后重跑。
-- 恢复：新增索引可保留；回退应用不要求删索引。禁止在共享/生产数据上试验。
-- demand_jd_resume_source 的候选人外键已有 InnoDB 支撑索引，本脚本不重复添加。

SET @candidate_merge_sql = IF(DATABASE() IS NOT NULL, 'DO 0', 'SELECT `candidate merge indexes require selected database`');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

-- 在执行任何 DDL 前检查全部对象；既有同名索引不兼容时停止，不自动删除/替换。
SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_candidate_selection_item' AND COLUMN_NAME='candidate_id'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii'
     AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO')=1,
    'DO 0', 'SELECT `matching_candidate_selection_item candidate_id prerequisite missing or incompatible`');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_candidate_selection_item' AND INDEX_NAME='ix_matching_selection_candidate')=0 OR
    ((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='matching_candidate_selection_item' AND INDEX_NAME='ix_matching_selection_candidate')=1 AND
     (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='matching_candidate_selection_item' AND INDEX_NAME='ix_matching_selection_candidate'
      AND SEQ_IN_INDEX=1 AND COLUMN_NAME='candidate_id' AND NON_UNIQUE=1 AND INDEX_TYPE='BTREE'
      AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND COLLATION='A')=1),
    'DO 0', 'SELECT `ix_matching_selection_candidate has incompatible definition`');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_match_run_item_outcome' AND COLUMN_NAME='candidate_id'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii'
     AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO')=1,
    'DO 0', 'SELECT `matching_match_run_item_outcome candidate_id prerequisite missing or incompatible`');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_match_run_item_outcome' AND INDEX_NAME='ix_matching_item_outcome_candidate')=0 OR
    ((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='matching_match_run_item_outcome' AND INDEX_NAME='ix_matching_item_outcome_candidate')=1 AND
     (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='matching_match_run_item_outcome' AND INDEX_NAME='ix_matching_item_outcome_candidate'
      AND SEQ_IN_INDEX=1 AND COLUMN_NAME='candidate_id' AND NON_UNIQUE=1 AND INDEX_TYPE='BTREE'
      AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND COLLATION='A')=1),
    'DO 0', 'SELECT `ix_matching_item_outcome_candidate has incompatible definition`');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_weighted_result' AND COLUMN_NAME='candidate_id'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii'
     AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO')=1,
    'DO 0', 'SELECT `matching_weighted_result candidate_id prerequisite missing or incompatible`');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_weighted_result' AND INDEX_NAME='ix_matching_result_candidate')=0 OR
    ((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='matching_weighted_result' AND INDEX_NAME='ix_matching_result_candidate')=1 AND
     (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='matching_weighted_result' AND INDEX_NAME='ix_matching_result_candidate'
      AND SEQ_IN_INDEX=1 AND COLUMN_NAME='candidate_id' AND NON_UNIQUE=1 AND INDEX_TYPE='BTREE'
      AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND COLLATION='A')=1),
    'DO 0', 'SELECT `ix_matching_result_candidate has incompatible definition`');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='submission_client_restriction_current' AND COLUMN_NAME='candidate_id'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii'
     AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO')=1,
    'DO 0', 'SELECT `submission_client_restriction_current candidate_id prerequisite missing or incompatible`');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='submission_client_restriction_current' AND INDEX_NAME='ix_client_restriction_current_candidate')=0 OR
    ((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='submission_client_restriction_current' AND INDEX_NAME='ix_client_restriction_current_candidate')=1 AND
     (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
      AND TABLE_NAME='submission_client_restriction_current' AND INDEX_NAME='ix_client_restriction_current_candidate'
      AND SEQ_IN_INDEX=1 AND COLUMN_NAME='candidate_id' AND NON_UNIQUE=1 AND INDEX_TYPE='BTREE'
      AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND COLLATION='A')=1),
    'DO 0', 'SELECT `ix_client_restriction_current_candidate has incompatible definition`');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_candidate_selection_item' AND INDEX_NAME='ix_matching_selection_candidate')=0,
    'ALTER TABLE matching_candidate_selection_item ADD INDEX ix_matching_selection_candidate (candidate_id)', 'DO 0');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_match_run_item_outcome' AND INDEX_NAME='ix_matching_item_outcome_candidate')=0,
    'ALTER TABLE matching_match_run_item_outcome ADD INDEX ix_matching_item_outcome_candidate (candidate_id)', 'DO 0');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_weighted_result' AND INDEX_NAME='ix_matching_result_candidate')=0,
    'ALTER TABLE matching_weighted_result ADD INDEX ix_matching_result_candidate (candidate_id)', 'DO 0');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;

SET @candidate_merge_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='submission_client_restriction_current' AND INDEX_NAME='ix_client_restriction_current_candidate')=0,
    'ALTER TABLE submission_client_restriction_current ADD INDEX ix_client_restriction_current_candidate (candidate_id)', 'DO 0');
PREPARE candidate_merge_stmt FROM @candidate_merge_sql;
EXECUTE candidate_merge_stmt;
DEALLOCATE PREPARE candidate_merge_stmt;


