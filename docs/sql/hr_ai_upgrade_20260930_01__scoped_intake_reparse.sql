-- 版本：20260930_01；为身份待核对收件保留独立重解析结果，不覆盖同内容的共享成功解析。
-- 前提：MySQL 8，已完成 20260928_01；备份目标库并停止旧版上传和解析 Worker 后执行。
-- 发布顺序：先升级结构并部署兼容作用域的应用，再开放独立重解析；旧应用无法按哈希唯一查找已有独立解析行。
-- 数据影响：历史处理行保持原哈希和结果，新增列默认全零；先建立组合唯一键再移除旧单列唯一键。
-- 重跑与部分完成：所有结构检查限定当前数据库；新增列、组合键和旧键清理可分别续跑。
-- 恢复判定：停止写入后核对 processing_scope_key <> '00000000000000000000000000' 的行数。
-- 若尚无独立解析行，可按经验证的部署回退方案恢复旧应用；仍须核对迁移历史与制品兼容性。
-- 若已有独立解析行，不可直接回退旧应用（按哈希查找将遇到多行）；保留处理行、收件绑定和模型事实，前滚到兼容作用域的版本。
SET NAMES utf8mb4;

SET @intake_scope_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing'
     AND COLUMN_NAME IN ('id','content_sha256','status'))=3,
    'DO 0', 'SELECT `scoped intake reparse requires talent_resume_intake_processing`');
PREPARE intake_scope_stmt FROM @intake_scope_sql;
EXECUTE intake_scope_stmt;
DEALLOCATE PREPARE intake_scope_stmt;

SET @intake_scope_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing' AND COLUMN_NAME='processing_scope_key')=0,
    'ALTER TABLE talent_resume_intake_processing ADD COLUMN processing_scope_key CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT ''00000000000000000000000000'' COMMENT ''全零为共享解析；独立重解析使用本次处理 ID'' AFTER content_sha256',
    'DO 0');
PREPARE intake_scope_stmt FROM @intake_scope_sql;
EXECUTE intake_scope_stmt;
DEALLOCATE PREPARE intake_scope_stmt;

SET @intake_scope_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing' AND COLUMN_NAME='processing_scope_key'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii'
     AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='NO'
     AND COLUMN_DEFAULT='00000000000000000000000000')=1,
    'DO 0', 'SELECT `processing_scope_key has an incompatible definition`');
PREPARE intake_scope_stmt FROM @intake_scope_sql;
EXECUTE intake_scope_stmt;
DEALLOCATE PREPARE intake_scope_stmt;

SET @intake_scope_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing'
     AND INDEX_NAME='uk_talent_intake_processing_hash')=0 OR
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing'
     AND INDEX_NAME='uk_talent_intake_processing_hash' AND NON_UNIQUE=0
     AND SEQ_IN_INDEX=1 AND COLUMN_NAME='content_sha256')=1 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing'
     AND INDEX_NAME='uk_talent_intake_processing_hash')=1,
    'DO 0', 'SELECT `old intake hash index has an incompatible definition`');
PREPARE intake_scope_stmt FROM @intake_scope_sql;
EXECUTE intake_scope_stmt;
DEALLOCATE PREPARE intake_scope_stmt;

SET @intake_scope_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing'
     AND INDEX_NAME='uk_talent_intake_processing_hash_scope')=0,
    'ALTER TABLE talent_resume_intake_processing ADD UNIQUE KEY uk_talent_intake_processing_hash_scope (content_sha256,processing_scope_key)',
    'DO 0');
PREPARE intake_scope_stmt FROM @intake_scope_sql;
EXECUTE intake_scope_stmt;
DEALLOCATE PREPARE intake_scope_stmt;

SET @intake_scope_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing'
     AND INDEX_NAME='uk_talent_intake_processing_hash_scope' AND NON_UNIQUE=0)=2 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing'
     AND INDEX_NAME='uk_talent_intake_processing_hash_scope' AND SEQ_IN_INDEX=1
     AND COLUMN_NAME='content_sha256')=1 AND
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing'
     AND INDEX_NAME='uk_talent_intake_processing_hash_scope' AND SEQ_IN_INDEX=2
     AND COLUMN_NAME='processing_scope_key')=1,
    'DO 0', 'SELECT `scoped intake hash index has an incompatible definition`');
PREPARE intake_scope_stmt FROM @intake_scope_sql;
EXECUTE intake_scope_stmt;
DEALLOCATE PREPARE intake_scope_stmt;

SET @intake_scope_sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake_processing'
     AND INDEX_NAME='uk_talent_intake_processing_hash')=1,
    'ALTER TABLE talent_resume_intake_processing DROP INDEX uk_talent_intake_processing_hash',
    'DO 0');
PREPARE intake_scope_stmt FROM @intake_scope_sql;
EXECUTE intake_scope_stmt;
DEALLOCATE PREPARE intake_scope_stmt;
