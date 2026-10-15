-- 版本：20260914_04；招聘月度汇报增加内部删除状态。
-- 前提：MySQL 8.0.16+，已完成 20260914_03 月报升级；客户端已选择目标数据库。
-- 部署：先备份目标库并停止应用及月报 Worker，账号须具有 ALTER 权限。
-- 数据影响：仅放宽 status CHECK，保留全部记录、附件引用、幂等键及审计；本脚本不删除文件。
-- 恢复：新旧 CHECK 在同一 ALTER 中原子替换，不存在无约束的提交窗口；缺少约束时可接续添加。
-- 重跑：每次先检查当前库元数据，已有 CHECK 可重复原子替换，DDL 失败不会清除原约束。
-- 回退：一旦新版产生 DELETED 记录，不得恢复旧 CHECK 或启动旧版，否则会拒绝记录或暴露已删除汇报。
-- 如必须回退，先停应用/Worker，核对并保留删除审计，按备份恢复数据库及关联对象；不得将 DELETED 改回可读状态。
-- Flyway 失败历史的处理遵循数据库 SQL 部署规范；不修改成功迁移的校验值。
SET NAMES utf8mb4;

SET @hr_monthly_delete_sql = IF(DATABASE() IS NOT NULL
 AND (SELECT COUNT(*) FROM information_schema.TABLES
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report')=1
 AND (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report'
        AND COLUMN_NAME='status' AND DATA_TYPE='varchar' AND CHARACTER_MAXIMUM_LENGTH=20
        AND IS_NULLABLE='NO' AND COLLATION_NAME='ascii_bin')=1
 AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
      WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report'
        AND CONSTRAINT_NAME='ck_reporting_monthly_report_status' AND CONSTRAINT_TYPE<>'CHECK')=0,
 'DO 0', 'SELECT `monthly report delete upgrade requires the monthly report schema in the selected database`');
PREPARE hr_monthly_delete_stmt FROM @hr_monthly_delete_sql;
EXECUTE hr_monthly_delete_stmt;
DEALLOCATE PREPARE hr_monthly_delete_stmt;

SET @hr_monthly_delete_sql = CONCAT('ALTER TABLE reporting_monthly_report ',
 IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report'
       AND CONSTRAINT_NAME='ck_reporting_monthly_report_status' AND CONSTRAINT_TYPE='CHECK')=1,
    'DROP CHECK ck_reporting_monthly_report_status, ', ''),
 'ADD CONSTRAINT ck_reporting_monthly_report_status CHECK (status IN
  (''UPLOADING'', ''VALIDATING'', ''INVALID'', ''READY'', ''QUEUED'', ''SUCCEEDED'', ''FAILED'', ''CANCELLED'', ''DELETED''))');
PREPARE hr_monthly_delete_stmt FROM @hr_monthly_delete_sql;
EXECUTE hr_monthly_delete_stmt;
DEALLOCATE PREPARE hr_monthly_delete_stmt;

SET @hr_monthly_delete_sql = NULL;
