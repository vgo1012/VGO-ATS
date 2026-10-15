-- 版本：20260914_03；招聘月度汇报持久化与专属权限。
-- 前提：MySQL 8，已部署收敛基线及 20260912_01 归属隔离升级，客户端已选择目标数据库。
-- 数据影响：新增两张月报表和 HR_DIRECTOR 专属业务权限；不改写既有报告、异步任务或角色数据。
-- 部署：先备份并停止旧应用/Worker。DDL 隐式提交；脚本逐项检查元数据，支持中断后的重跑。
-- 恢复：保留新表及已上传对象以免丢失审计证据；回退应用前停止月报 Worker，并从备份恢复表/权限。
SET NAMES utf8mb4;

SET @hr_monthly_sql = IF(DATABASE() IS NOT NULL AND
 (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME IN ('identity_role','identity_permission','identity_role_permission','reporting_async_task'))=4,
 'DO 0', 'SELECT `monthly report upgrade requires the converged schema in the selected database`');
PREPARE hr_monthly_stmt FROM @hr_monthly_sql;
EXECUTE hr_monthly_stmt;
DEALLOCATE PREPARE hr_monthly_stmt;

SET @hr_monthly_sql = IF(
 (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report')=0,
 'CREATE TABLE reporting_monthly_report (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT ''主键 ID'',
    report_month CHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''报告月份，格式 YYYY-MM'',
    data_as_of DATE NOT NULL COMMENT ''数据截至日期'',
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''当前状态'',
    task_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''异步任务 ID，上传阶段可为空'',
    phase VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''当前处理阶段'',
    owner_user_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''创建并负责的用户 ID'',
    owner_department_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''创建时归属部门 ID'',
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''创建请求幂等键'',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''创建请求指纹'',
    generation_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''报告内生成幂等键'',
    generation_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''生成请求指纹'',
    management_remarks VARCHAR(2000) NULL COMMENT ''管理备注'',
    snapshot_json LONGTEXT NULL COMMENT ''冻结的报告快照与诊断 JSON'',
    offer_count INT NULL COMMENT ''录用明细数量'',
    total_score DECIMAL(18,2) NULL COMMENT ''录用总分'',
    error_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''失败编码'',
    error_detail VARCHAR(500) NULL COMMENT ''可安全展示的失败详情'',
    expires_at TIMESTAMP(6) NULL COMMENT ''报告及附件最早清理时间'',
    ext TEXT NULL COMMENT ''JSON 扩展信息'', remark VARCHAR(255) NULL COMMENT ''备注'',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''创建人 ID'', create_name VARCHAR(64) NULL COMMENT ''创建人名称'',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''创建时间'',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''更新人 ID'', update_name VARCHAR(64) NULL COMMENT ''更新人名称'',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT ''更新时间'',
    version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号'',
    UNIQUE KEY uk_reporting_monthly_report_request (owner_user_id,idempotency_key),
    INDEX ix_reporting_monthly_report_history (create_time DESC,id DESC),
    INDEX ix_reporting_monthly_report_month_history (report_month,create_time DESC,id DESC),
    INDEX ix_reporting_monthly_report_active (status,create_time,id),
    CONSTRAINT fk_reporting_monthly_report_task FOREIGN KEY (task_id) REFERENCES reporting_async_task(id) ON DELETE RESTRICT,
    CONSTRAINT ck_reporting_monthly_report_status CHECK (status IN (''UPLOADING'',''VALIDATING'',''INVALID'',''READY'',''QUEUED'',''SUCCEEDED'',''FAILED'',''CANCELLED'')),
    CONSTRAINT ck_reporting_monthly_report_phase CHECK (phase IN (''UPLOAD'',''VALIDATE'',''GENERATE'')),
    CONSTRAINT ck_reporting_monthly_report_month CHECK (report_month REGEXP ''^[0-9]{4}-(0[1-9]|1[0-2])$''),
    CONSTRAINT ck_reporting_monthly_report_score CHECK (total_score IS NULL OR total_score >= 0),
    CONSTRAINT ck_reporting_monthly_report_offer_count CHECK (offer_count IS NULL OR offer_count >= 0)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''招聘月度汇报记录''',
 IF((SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report')=28
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report'
           AND COLUMN_NAME IN ('id','report_month','data_as_of','status','task_id','phase','owner_user_id','owner_department_id','idempotency_key','request_fingerprint','generation_key','generation_fingerprint','management_remarks','snapshot_json','offer_count','total_score','error_code','error_detail','expires_at','ext','remark','create_id','create_name','create_time','update_id','update_name','update_time','version'))=28
    AND (SELECT GROUP_CONCAT(CONCAT(INDEX_NAME,':',NON_UNIQUE,':',INDEX_TYPE,':',COLUMN_NAME)
            ORDER BY INDEX_NAME,SEQ_IN_INDEX SEPARATOR '|')
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report'
           AND INDEX_NAME IN ('PRIMARY','fk_reporting_monthly_report_task','uk_reporting_monthly_report_request','ix_reporting_monthly_report_history','ix_reporting_monthly_report_month_history','ix_reporting_monthly_report_active'))
        = 'fk_reporting_monthly_report_task:1:BTREE:task_id|ix_reporting_monthly_report_active:1:BTREE:status|ix_reporting_monthly_report_active:1:BTREE:create_time|ix_reporting_monthly_report_active:1:BTREE:id|ix_reporting_monthly_report_history:1:BTREE:create_time|ix_reporting_monthly_report_history:1:BTREE:id|ix_reporting_monthly_report_month_history:1:BTREE:report_month|ix_reporting_monthly_report_month_history:1:BTREE:create_time|ix_reporting_monthly_report_month_history:1:BTREE:id|PRIMARY:0:BTREE:id|uk_reporting_monthly_report_request:0:BTREE:owner_user_id|uk_reporting_monthly_report_request:0:BTREE:idempotency_key'
    AND (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report')=6
    AND (SELECT GROUP_CONCAT(CONCAT(LOWER(CONSTRAINT_NAME),':',LOWER(CONSTRAINT_TYPE))
            ORDER BY CONSTRAINT_NAME SEPARATOR '|')
         FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report'
           AND CONSTRAINT_NAME IN ('PRIMARY','uk_reporting_monthly_report_request','fk_reporting_monthly_report_task','ck_reporting_monthly_report_status','ck_reporting_monthly_report_phase','ck_reporting_monthly_report_month','ck_reporting_monthly_report_score','ck_reporting_monthly_report_offer_count'))
        = 'ck_reporting_monthly_report_month:check|ck_reporting_monthly_report_offer_count:check|ck_reporting_monthly_report_phase:check|ck_reporting_monthly_report_score:check|ck_reporting_monthly_report_status:check|fk_reporting_monthly_report_task:foreign key|primary:primary key|uk_reporting_monthly_report_request:unique'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report')=8
    AND (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report'
           AND CONSTRAINT_NAME='fk_reporting_monthly_report_task' AND REFERENCED_TABLE_NAME='reporting_async_task'
           AND DELETE_RULE='RESTRICT')=1,
 'DO 0','SELECT `existing reporting_monthly_report has an incompatible definition`'));
PREPARE hr_monthly_stmt FROM @hr_monthly_sql;
EXECUTE hr_monthly_stmt;
DEALLOCATE PREPARE hr_monthly_stmt;

SET @hr_monthly_sql = IF(
 (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report_file')=0,
 'CREATE TABLE reporting_monthly_report_file (
    id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY COMMENT ''主键 ID'',
    report_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''所属月度汇报 ID'',
    file_kind VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''文件用途'',
    file_name VARCHAR(200) NOT NULL COMMENT ''原始或产出文件名'',
    object_key VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''对象存储键'',
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT ''内容 SHA-256 摘要'',
    size_bytes BIGINT NOT NULL COMMENT ''文件大小（字节）'', expires_at TIMESTAMP(6) NULL COMMENT ''最早清理时间'', deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT ''对象是否已删除'',
    ext TEXT NULL COMMENT ''JSON 扩展信息'', remark VARCHAR(255) NULL COMMENT ''备注'',
    create_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''创建人 ID'', create_name VARCHAR(64) NULL COMMENT ''创建人名称'',
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT ''创建时间'',
    update_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''更新人 ID'', update_name VARCHAR(64) NULL COMMENT ''更新人名称'',
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT ''更新时间'',
    version INT NOT NULL DEFAULT 0 COMMENT ''技术乐观锁版本号'',
    UNIQUE KEY uk_reporting_monthly_report_file_object (object_key),
    INDEX ix_reporting_monthly_report_file_kind (report_id,file_kind),
    INDEX ix_reporting_monthly_report_file_expiry (deleted,expires_at,id),
    CONSTRAINT fk_reporting_monthly_report_file_report FOREIGN KEY (report_id) REFERENCES reporting_monthly_report(id) ON DELETE RESTRICT,
    CONSTRAINT ck_reporting_monthly_report_file_kind CHECK (file_kind IN (''OFFER'',''DEMAND'',''REPORT_TEMP'',''REPORT'')),
    CONSTRAINT ck_reporting_monthly_report_file_size CHECK (size_bytes > 0)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''招聘月度汇报文件''',
 IF((SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report_file')=18
    AND (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report_file'
           AND COLUMN_NAME IN ('id','report_id','file_kind','file_name','object_key','content_hash','size_bytes','expires_at','deleted','ext','remark','create_id','create_name','create_time','update_id','update_name','update_time','version'))=18
    AND (SELECT GROUP_CONCAT(CONCAT(INDEX_NAME,':',NON_UNIQUE,':',INDEX_TYPE,':',COLUMN_NAME)
            ORDER BY INDEX_NAME,SEQ_IN_INDEX SEPARATOR '|')
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report_file'
           AND INDEX_NAME IN ('PRIMARY','uk_reporting_monthly_report_file_object','ix_reporting_monthly_report_file_kind','ix_reporting_monthly_report_file_expiry'))
        = 'ix_reporting_monthly_report_file_expiry:1:BTREE:deleted|ix_reporting_monthly_report_file_expiry:1:BTREE:expires_at|ix_reporting_monthly_report_file_expiry:1:BTREE:id|ix_reporting_monthly_report_file_kind:1:BTREE:report_id|ix_reporting_monthly_report_file_kind:1:BTREE:file_kind|PRIMARY:0:BTREE:id|uk_reporting_monthly_report_file_object:0:BTREE:object_key'
    AND (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report_file')=4
    AND (SELECT GROUP_CONCAT(CONCAT(LOWER(CONSTRAINT_NAME),':',LOWER(CONSTRAINT_TYPE))
            ORDER BY CONSTRAINT_NAME SEPARATOR '|')
         FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report_file'
           AND CONSTRAINT_NAME IN ('PRIMARY','uk_reporting_monthly_report_file_object','fk_reporting_monthly_report_file_report','ck_reporting_monthly_report_file_kind','ck_reporting_monthly_report_file_size'))
        = 'ck_reporting_monthly_report_file_kind:check|ck_reporting_monthly_report_file_size:check|fk_reporting_monthly_report_file_report:foreign key|primary:primary key|uk_reporting_monthly_report_file_object:unique'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report_file')=5
    AND (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='reporting_monthly_report_file'
           AND CONSTRAINT_NAME='fk_reporting_monthly_report_file_report' AND REFERENCED_TABLE_NAME='reporting_monthly_report'
           AND DELETE_RULE='RESTRICT')=1,
 'DO 0','SELECT `existing reporting_monthly_report_file has an incompatible definition`'));
PREPARE hr_monthly_stmt FROM @hr_monthly_sql;
EXECUTE hr_monthly_stmt;
DEALLOCATE PREPARE hr_monthly_stmt;

INSERT IGNORE INTO identity_permission(code,name,category,create_time)
VALUES ('reporting.monthly-report.manage','管理招聘月度汇报','REPORTING',UTC_TIMESTAMP(6));

INSERT IGNORE INTO identity_role_permission(role_id,permission_code)
SELECT r.id,p.code FROM identity_role r JOIN identity_permission p
WHERE r.code='HR_DIRECTOR' AND p.code='reporting.monthly-report.manage';

SET @hr_monthly_sql = NULL;
