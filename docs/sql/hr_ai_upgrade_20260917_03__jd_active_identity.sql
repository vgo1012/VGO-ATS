-- 版本：20260917_03；为未删除 JD 增加“岗位名称 + 区分说明”的全局唯一约束。
-- 前提：MySQL 8，已完成此前迁移并明确选择目标数据库；升级前停止旧版应用写入并备份 demand_jd。
-- 数据影响：不修改或删除业务数据；历史空/纯空白区分说明继续保留且不纳入唯一约束。
-- 冲突处理：若未删除且区分说明非空的历史 JD 经 TRIM 后同名同说明，迁移以描述性 SQL 错误中止；
--           请人工核对并通过正常业务流程更正或逻辑删除冲突记录后重跑，脚本不得代为删改。
-- 重跑与部分完成：每步检查当前数据库元数据；生成列或唯一索引已兼容存在时跳过，缺失时补齐。
-- 恢复：应用回退可保留生成列及唯一索引；确需移除时先停止写入、备份并确认不会重新产生重复数据。
SET NAMES utf8mb4;

SET @jd_identity_sql = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME IN ('title','distinction','deleted'))=3,
    'DO 0', 'SELECT `JD identity upgrade requires an InnoDB demand_jd table with title, distinction and deleted`');
PREPARE jd_identity_stmt FROM @jd_identity_sql;
EXECUTE jd_identity_stmt;
DEALLOCATE PREPARE jd_identity_stmt;

SET @jd_identity_sql = IF((SELECT COUNT(*) FROM (
    SELECT 1 FROM demand_jd
    WHERE deleted=FALSE AND distinction IS NOT NULL AND CHAR_LENGTH(TRIM(distinction))>0
    GROUP BY SHA2(CONCAT(CHAR_LENGTH(TRIM(title)), ':', TRIM(title), TRIM(distinction)), 256)
    HAVING COUNT(*)>1
    LIMIT 1) duplicate_identity)=0,
    'DO 0', 'SELECT `duplicate active JD title and distinction; resolve manually before upgrading`');
PREPARE jd_identity_stmt FROM @jd_identity_sql;
EXECUTE jd_identity_stmt;
DEALLOCATE PREPARE jd_identity_stmt;

SET @jd_identity_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND COLUMN_NAME='active_identity_key')=0,
    'ALTER TABLE demand_jd ADD COLUMN active_identity_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN deleted = FALSE AND distinction IS NOT NULL AND CHAR_LENGTH(TRIM(distinction)) > 0 THEN SHA2(CONCAT(CHAR_LENGTH(TRIM(title)), '':'', TRIM(title), TRIM(distinction)), 256) ELSE NULL END) STORED COMMENT ''未删除 JD 的岗位名称与区分说明全局唯一键'' AFTER deleted',
    IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='demand_jd' AND COLUMN_NAME='active_identity_key'
        AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=64
        AND CHARACTER_SET_NAME='ascii' AND COLLATION_NAME='ascii_bin'
        AND IS_NULLABLE='YES' AND EXTRA LIKE '%STORED GENERATED%'
        AND REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(GENERATION_EXPRESSION),
            '_utf8mb4', ''), 'false', '0'), CHAR(96), ''), CHAR(92), ''),
            '[()[:space:]]+', '')=
            'casewhendeleted=0anddistinctionisnotnullandchar_lengthtrimdistinction>0thensha2concatchar_lengthtrimtitle,'':'',trimtitle,trimdistinction,256elsenullend')=1,
       'DO 0', 'SELECT `existing demand_jd.active_identity_key has an incompatible definition`'));
PREPARE jd_identity_stmt FROM @jd_identity_sql;
EXECUTE jd_identity_stmt;
DEALLOCATE PREPARE jd_identity_stmt;

SET @jd_identity_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND INDEX_NAME='uk_jd_active_identity')=0,
    'ALTER TABLE demand_jd ADD UNIQUE INDEX uk_jd_active_identity (active_identity_key)',
    IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='demand_jd' AND INDEX_NAME='uk_jd_active_identity')=1 AND
       (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
        AND TABLE_NAME='demand_jd' AND INDEX_NAME='uk_jd_active_identity'
        AND NON_UNIQUE=0 AND SEQ_IN_INDEX=1 AND COLUMN_NAME='active_identity_key'
        AND SUB_PART IS NULL)=1,
       'DO 0', 'SELECT `existing uk_jd_active_identity has an incompatible definition`'));
PREPARE jd_identity_stmt FROM @jd_identity_sql;
EXECUTE jd_identity_stmt;
DEALLOCATE PREPARE jd_identity_stmt;
SET @jd_identity_sql = NULL;
