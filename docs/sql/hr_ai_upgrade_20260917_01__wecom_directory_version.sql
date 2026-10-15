-- 版本：20260917_01；补齐企业微信早期预建目录表缺失的乐观锁列。
-- 前提：MySQL 8，已执行 20260916_01，明确选择目标数据库；保持目录同步关闭并备份四表。
-- 数据影响：仅为缺列的目录表增加 version INT NOT NULL DEFAULT 0；已有版本及业务数据不改写。
-- 原因：CREATE TABLE IF NOT EXISTS 不会修正早期预建表，迁移历史成功不能证明表内列完整。
-- 重跑与部分完成：先检查全部表及已存在列的兼容性，每张表独立原子增加缺列；已完成步骤跳过。
-- 恢复：中断后保持同步关闭并重跑；应用回退保留新增列，不删除目录、账号、关系或批次。
SET NAMES utf8mb4;

SET @wecom_version_sql = IF(DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('identity_wecom_sync_state','identity_wecom_sync_batch','identity_wecom_department','identity_wecom_member')
     AND ENGINE='InnoDB')=4,
    'DO 0', 'SELECT `wecom version upgrade requires four InnoDB directory tables in the selected database`');
PREPARE wecom_version_stmt FROM @wecom_version_sql;
EXECUTE wecom_version_stmt;
DEALLOCATE PREPARE wecom_version_stmt;

SET @wecom_version_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('identity_wecom_sync_state','identity_wecom_sync_batch','identity_wecom_department','identity_wecom_member')
     AND COLUMN_NAME='version' AND (DATA_TYPE<>'int' OR COLUMN_TYPE LIKE '%unsigned%'
         OR IS_NULLABLE<>'NO' OR COLUMN_DEFAULT IS NULL OR COLUMN_DEFAULT<>'0' OR EXTRA<>''))=0,
    'DO 0', 'SELECT `existing wecom version column is incompatible; inspect before upgrading`');
PREPARE wecom_version_stmt FROM @wecom_version_sql;
EXECUTE wecom_version_stmt;
DEALLOCATE PREPARE wecom_version_stmt;

SET @wecom_version_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_wecom_sync_state' AND COLUMN_NAME='version')=0,
    'ALTER TABLE identity_wecom_sync_state ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号''', 'DO 0');
PREPARE wecom_version_stmt FROM @wecom_version_sql;
EXECUTE wecom_version_stmt;
DEALLOCATE PREPARE wecom_version_stmt;

SET @wecom_version_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_wecom_sync_batch' AND COLUMN_NAME='version')=0,
    'ALTER TABLE identity_wecom_sync_batch ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号''', 'DO 0');
PREPARE wecom_version_stmt FROM @wecom_version_sql;
EXECUTE wecom_version_stmt;
DEALLOCATE PREPARE wecom_version_stmt;

SET @wecom_version_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_wecom_department' AND COLUMN_NAME='version')=0,
    'ALTER TABLE identity_wecom_department ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号''', 'DO 0');
PREPARE wecom_version_stmt FROM @wecom_version_sql;
EXECUTE wecom_version_stmt;
DEALLOCATE PREPARE wecom_version_stmt;

SET @wecom_version_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_wecom_member' AND COLUMN_NAME='version')=0,
    'ALTER TABLE identity_wecom_member ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号''', 'DO 0');
PREPARE wecom_version_stmt FROM @wecom_version_sql;
EXECUTE wecom_version_stmt;
DEALLOCATE PREPARE wecom_version_stmt;
SET @wecom_version_sql = NULL;
