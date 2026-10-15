-- 版本：20260928_01；为每个账号持久化登录失败窗口和临时锁定状态。
-- 前提：MySQL 8，已执行至 20260924_01；在已核对的目标库备份后停止旧版登录写入。
-- 数据影响：仅新增列，历史账号失败次数默认为 0，不修改凭据、权限和已有会话。
-- 重跑与部分完成：每列按当前库元数据检查；中断后可继续，已有列定义不兼容时明确失败。
-- 恢复：旧版应用可忽略新增列；回退应用时保留列，勿清除失败审计或账号数据。
SET NAMES utf8mb4;

SET @login_guard_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_user_account' AND ENGINE='InnoDB')=1 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_user_account' AND COLUMN_NAME IN ('id','normalized_email','credential_hash'))=3,
    'DO 0', 'SELECT `login protection upgrade requires identity_user_account`');
PREPARE login_guard_stmt FROM @login_guard_sql;
EXECUTE login_guard_stmt;
DEALLOCATE PREPARE login_guard_stmt;

SET @login_guard_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_user_account' AND COLUMN_NAME='failed_login_count')=0,
    'ALTER TABLE identity_user_account ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0 COMMENT ''当前登录失败窗口内的次数''',
    'DO 0');
PREPARE login_guard_stmt FROM @login_guard_sql;
EXECUTE login_guard_stmt;
DEALLOCATE PREPARE login_guard_stmt;

SET @login_guard_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_user_account' AND COLUMN_NAME='failed_login_count'
     AND DATA_TYPE='int' AND IS_NULLABLE='NO' AND COLUMN_DEFAULT='0')=1,
    'DO 0', 'SELECT `failed_login_count has an incompatible definition`');
PREPARE login_guard_stmt FROM @login_guard_sql;
EXECUTE login_guard_stmt;
DEALLOCATE PREPARE login_guard_stmt;

SET @login_guard_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_user_account' AND COLUMN_NAME='failed_login_window_started_at')=0,
    'ALTER TABLE identity_user_account ADD COLUMN failed_login_window_started_at TIMESTAMP(6) NULL COMMENT ''登录失败窗口起点''',
    'DO 0');
PREPARE login_guard_stmt FROM @login_guard_sql;
EXECUTE login_guard_stmt;
DEALLOCATE PREPARE login_guard_stmt;

SET @login_guard_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_user_account' AND COLUMN_NAME='failed_login_window_started_at'
     AND DATA_TYPE='timestamp' AND DATETIME_PRECISION=6 AND IS_NULLABLE='YES')=1,
    'DO 0', 'SELECT `failed_login_window_started_at has an incompatible definition`');
PREPARE login_guard_stmt FROM @login_guard_sql;
EXECUTE login_guard_stmt;
DEALLOCATE PREPARE login_guard_stmt;

SET @login_guard_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_user_account' AND COLUMN_NAME='login_locked_until')=0,
    'ALTER TABLE identity_user_account ADD COLUMN login_locked_until TIMESTAMP(6) NULL COMMENT ''登录锁定截止时间''',
    'DO 0');
PREPARE login_guard_stmt FROM @login_guard_sql;
EXECUTE login_guard_stmt;
DEALLOCATE PREPARE login_guard_stmt;

SET @login_guard_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='identity_user_account' AND COLUMN_NAME='login_locked_until'
     AND DATA_TYPE='timestamp' AND DATETIME_PRECISION=6 AND IS_NULLABLE='YES')=1,
    'DO 0', 'SELECT `login_locked_until has an incompatible definition`');
PREPARE login_guard_stmt FROM @login_guard_sql;
EXECUTE login_guard_stmt;
DEALLOCATE PREPARE login_guard_stmt;

SET @login_guard_sql = NULL;
