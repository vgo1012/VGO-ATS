-- 版本：20261008_01；保存企业微信创建意图与确认回执，未知结果禁止重新创建。
-- 前提：MySQL 8，已部署 20260917_04 企微面试投影；备份目标库并停止全部旧版企微 Worker/写入实例。
-- 数据影响：新增可空意图指针并扩展两个状态 CHECK；旧版在途 CREATE、模糊网络失败转 UNKNOWN，保留记录等待核对。
-- 历史限制：旧版已清掉错误/创建状态的记录无法由数据库证明是否已创建，上线前需人工核对这类历史对象。
-- 重跑与部分完成：使用当前 DATABASE() 元数据；列/约束独立检查，意图 ID 确定生成；意图缺失时可补齐，不删除业务或审计。
-- 恢复：未知状态须核对原请求已终止与远端结果后恢复。回退应用前停止 Worker，保留新列和未知记录，不能让旧版恢复自动创建。
SET NAMES utf8mb4;

SET @wecom_create_sql = IF(
    DATABASE() IS NOT NULL AND
    (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME IN ('interview_external_calendar_projection','interview_external_calendar_attempt') AND ENGINE='InnoDB')=2 AND
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_projection' AND COLUMN_NAME IN
       ('id','desired_type','active_type','status','operation_revision','lease_token','lease_until','version'))=8,
    'DO 0', 'SELECT `wecom create recovery requires deployed calendar tables`');
PREPARE wecom_create_stmt FROM @wecom_create_sql;
EXECUTE wecom_create_stmt;
DEALLOCATE PREPARE wecom_create_stmt;

-- 先拒绝不兼容的已存在列，避免部分 DDL 后才发现配置错误。
SET @wecom_create_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_projection' AND COLUMN_NAME='create_intent_id')=0 OR
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_projection' AND COLUMN_NAME='create_intent_id'
     AND DATA_TYPE='char' AND CHARACTER_MAXIMUM_LENGTH=26 AND CHARACTER_SET_NAME='ascii'
     AND COLLATION_NAME='ascii_bin' AND IS_NULLABLE='YES')=1,
    'DO 0', 'SELECT `create_intent_id has an incompatible definition`');
PREPARE wecom_create_stmt FROM @wecom_create_sql;
EXECUTE wecom_create_stmt;
DEALLOCATE PREPARE wecom_create_stmt;

SET @wecom_create_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_projection' AND COLUMN_NAME='create_intent_id')=0,
    'ALTER TABLE interview_external_calendar_projection ADD COLUMN create_intent_id CHAR(26) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''未核对创建意图 ID，独立于处理租约''',
    'DO 0');
PREPARE wecom_create_stmt FROM @wecom_create_sql;
EXECUTE wecom_create_stmt;
DEALLOCATE PREPARE wecom_create_stmt;

SET @wecom_create_sql = IF(
    (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND CONSTRAINT_NAME='ck_interview_external_calendar_status' AND CHECK_CLAUSE NOT LIKE '%UNKNOWN%')=1,
    'ALTER TABLE interview_external_calendar_projection DROP CHECK ck_interview_external_calendar_status', 'DO 0');
PREPARE wecom_create_stmt FROM @wecom_create_sql;
EXECUTE wecom_create_stmt;
DEALLOCATE PREPARE wecom_create_stmt;

SET @wecom_create_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_projection' AND CONSTRAINT_NAME='ck_interview_external_calendar_status')=0,
    'ALTER TABLE interview_external_calendar_projection ADD CONSTRAINT ck_interview_external_calendar_status CHECK (status IN (''PENDING'',''PROCESSING'',''SYNCED'',''CANCELLED'',''FAILED'',''UNKNOWN''))', 'DO 0');
PREPARE wecom_create_stmt FROM @wecom_create_sql;
EXECUTE wecom_create_stmt;
DEALLOCATE PREPARE wecom_create_stmt;

SET @wecom_create_sql = IF(
    (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND CONSTRAINT_NAME='ck_interview_external_attempt_status'
     AND (CHECK_CLAUSE NOT LIKE '%RUNNING%' OR CHECK_CLAUSE NOT LIKE '%UNKNOWN%'))=1,
    'ALTER TABLE interview_external_calendar_attempt DROP CHECK ck_interview_external_attempt_status', 'DO 0');
PREPARE wecom_create_stmt FROM @wecom_create_sql;
EXECUTE wecom_create_stmt;
DEALLOCATE PREPARE wecom_create_stmt;

SET @wecom_create_sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()
     AND TABLE_NAME='interview_external_calendar_attempt' AND CONSTRAINT_NAME='ck_interview_external_attempt_status')=0,
    'ALTER TABLE interview_external_calendar_attempt ADD CONSTRAINT ck_interview_external_attempt_status CHECK (status IN (''RUNNING'',''SUCCEEDED'',''FAILED'',''UNKNOWN''))', 'DO 0');
PREPARE wecom_create_stmt FROM @wecom_create_sql;
EXECUTE wecom_create_stmt;
DEALLOCATE PREPARE wecom_create_stmt;

-- 已停止旧 Worker 后再隔离历史未知创建；重复执行不会覆盖已形成的新协议意图或核对结果。
UPDATE interview_external_calendar_projection
SET create_intent_id=UPPER(LEFT(SHA2(CONCAT('wecom-create-legacy:',id,':',operation_revision),256),26)),
    last_error_detail=LEFT(CONCAT('旧版创建结果待核对（',COALESCE(last_error_code,'PROCESSING'),'）：',COALESCE(last_error_detail,'')),500),
    last_error_code='WECOM_LEGACY_CREATE_UNKNOWN', status='UNKNOWN',
    lease_token=NULL, lease_until=NULL, update_time=CURRENT_TIMESTAMP(6), version=version+1
WHERE create_intent_id IS NULL AND active_type IS NULL AND desired_type IN ('WECOM_MEETING','WECOM_SCHEDULE')
  AND (status='PROCESSING' OR (status='FAILED' AND last_error_code IN
       ('WECOM_503','WECOM_CALL_EXCEPTION','WECOM_INVALID_RESPONSE','INVALID_ADAPTER_RESULT')));

INSERT INTO interview_external_calendar_attempt
    (id,projection_id,operation_revision,operation_type,external_type,status,error_code,error_detail,occurred_at,create_time,update_time,version)
SELECT p.create_intent_id,p.id,p.operation_revision,'CREATE',p.desired_type,'UNKNOWN',
       p.last_error_code,p.last_error_detail,p.update_time,p.update_time,p.update_time,0
FROM interview_external_calendar_projection p
WHERE p.status='UNKNOWN' AND p.last_error_code='WECOM_LEGACY_CREATE_UNKNOWN' AND p.create_intent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM interview_external_calendar_attempt a WHERE a.id=p.create_intent_id);

SET @wecom_create_sql = NULL;
