-- 版本：20260912_01；归属隔离与四级招聘角色。
-- 前提：MySQL 8，已部署 2026-09-12 收敛基线，客户端已选择目标数据库。
-- 数据影响：新增人力负责人角色（不自动授予任何账号）、业务权限和非唯一索引。
-- 共享简历由应用按登录身份开放，不额外给协作角色授予私有读取权限。部门管理名称统一。
-- 历史 public_visible 归零，保留归属、内容指纹、版本及所有招聘关联；不猜测归属 HR。
-- 部署：先备份并停止旧应用/Worker；索引构建需维护窗口。DDL 隐式提交，支持部分完成后重跑。
-- 恢复：备份保留旧角色权限和公共标记；不要直接启动会再次扩大数据范围的旧版本。
-- 回退索引前确认无查询提示依赖；恢复公共标记须从备份按主键核对，不重新归属或删除业务数据。
SET NAMES utf8mb4;

SET @hr_scope_sql = IF(DATABASE() IS NOT NULL AND
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME IN ('identity_role','identity_permission','identity_role_permission',
                    'demand_jd','talent_candidate','talent_resume_intake','matching_weight_profile'))=7,
 'DO 0', 'SELECT `owner scope upgrade requires the converged schema in the selected database`');
PREPARE hr_scope_stmt FROM @hr_scope_sql;
EXECUTE hr_scope_stmt;
DEALLOCATE PREPARE hr_scope_stmt;

INSERT INTO identity_role (id,code,name,system_role,status,version,create_time)
SELECT '00000000000000000000000005','HR_DIRECTOR','人力负责人',TRUE,'ACTIVE',0,UTC_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM identity_role WHERE code='HR_DIRECTOR');
UPDATE identity_role SET name='部门管理' WHERE code='RECRUITMENT_MANAGER' AND name<>'部门管理';
UPDATE identity_permission SET name='查看招聘简历工作数据'
WHERE code='talent.read' AND name<>'查看招聘简历工作数据';

-- 人力负责人只有业务权限；管理员配置仍由 SUPER_ADMIN 身份保护。
INSERT INTO identity_role_permission (role_id, permission_code)
SELECT r.id, p.code FROM identity_role r JOIN identity_permission p
WHERE r.code = 'HR_DIRECTOR' AND p.category IN
('DEMAND','TALENT','MATCHING','RECRUITMENT','INTERVIEW','RISK','SUBMISSION','REPORTING','AUDIT')
AND NOT EXISTS (SELECT 1 FROM identity_role_permission existing
                WHERE existing.role_id = r.id AND existing.permission_code = p.code);


UPDATE demand_jd SET public_visible=FALSE, version=version+1 WHERE public_visible=TRUE;

SET @hr_scope_sql = IF(
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_owner_page')=0,
 'ALTER TABLE demand_jd ADD INDEX ix_demand_jd_owner_page (owner_user_id, deleted, create_time DESC, id DESC)',
 IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_owner_page')=4
 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='demand_jd' AND INDEX_NAME='ix_demand_jd_owner_page' AND NON_UNIQUE=1
     AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='owner_user_id' AND COLLATION='A') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='deleted' AND COLLATION='A') OR (SEQ_IN_INDEX=3 AND COLUMN_NAME='create_time' AND COLLATION='D') OR (SEQ_IN_INDEX=4 AND COLUMN_NAME='id' AND COLLATION='D')))=4,
 'DO 0','SELECT `existing ix_demand_jd_owner_page has an incompatible definition`'));
PREPARE hr_scope_stmt FROM @hr_scope_sql;
EXECUTE hr_scope_stmt;
DEALLOCATE PREPARE hr_scope_stmt;

SET @hr_scope_sql = IF(
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='talent_candidate' AND INDEX_NAME='ix_talent_candidate_owner_page')=0,
 'ALTER TABLE talent_candidate ADD INDEX ix_talent_candidate_owner_page (owner_user_id, update_time, id)',
 IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_candidate' AND INDEX_NAME='ix_talent_candidate_owner_page')=3
 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_candidate' AND INDEX_NAME='ix_talent_candidate_owner_page' AND NON_UNIQUE=1
     AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='owner_user_id' AND COLLATION='A') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='update_time' AND COLLATION='A') OR (SEQ_IN_INDEX=3 AND COLUMN_NAME='id' AND COLLATION='A')))=3,
 'DO 0','SELECT `existing ix_talent_candidate_owner_page has an incompatible definition`'));
PREPARE hr_scope_stmt FROM @hr_scope_sql;
EXECUTE hr_scope_stmt;
DEALLOCATE PREPARE hr_scope_stmt;

SET @hr_scope_sql = IF(
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='talent_resume_intake' AND INDEX_NAME='ix_talent_intake_owner_page')=0,
 'ALTER TABLE talent_resume_intake ADD INDEX ix_talent_intake_owner_page (owner_user_id, create_time, id)',
 IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake' AND INDEX_NAME='ix_talent_intake_owner_page')=3
 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='talent_resume_intake' AND INDEX_NAME='ix_talent_intake_owner_page' AND NON_UNIQUE=1
     AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='owner_user_id' AND COLLATION='A') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='create_time' AND COLLATION='A') OR (SEQ_IN_INDEX=3 AND COLUMN_NAME='id' AND COLLATION='A')))=3,
 'DO 0','SELECT `existing ix_talent_intake_owner_page has an incompatible definition`'));
PREPARE hr_scope_stmt FROM @hr_scope_sql;
EXECUTE hr_scope_stmt;
DEALLOCATE PREPARE hr_scope_stmt;

SET @hr_scope_sql = IF(
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='matching_weight_profile' AND INDEX_NAME='ix_matching_weight_profile_owner_page')=0,
 'ALTER TABLE matching_weight_profile ADD INDEX ix_matching_weight_profile_owner_page (owner_user_id, update_time DESC, id DESC)',
 IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_weight_profile' AND INDEX_NAME='ix_matching_weight_profile_owner_page')=3
 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_weight_profile' AND INDEX_NAME='ix_matching_weight_profile_owner_page' AND NON_UNIQUE=1
     AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='owner_user_id' AND COLLATION='A') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='update_time' AND COLLATION='D') OR (SEQ_IN_INDEX=3 AND COLUMN_NAME='id' AND COLLATION='D')))=3,
 'DO 0','SELECT `existing ix_matching_weight_profile_owner_page has an incompatible definition`'));
PREPARE hr_scope_stmt FROM @hr_scope_sql;
EXECUTE hr_scope_stmt;
DEALLOCATE PREPARE hr_scope_stmt;

SET @hr_scope_sql = IF(
 (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='matching_weight_profile' AND INDEX_NAME='ix_matching_weight_profile_page')=0,
 'ALTER TABLE matching_weight_profile ADD INDEX ix_matching_weight_profile_page (update_time DESC, id DESC)',
 IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_weight_profile' AND INDEX_NAME='ix_matching_weight_profile_page')=2
 AND (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
     AND TABLE_NAME='matching_weight_profile' AND INDEX_NAME='ix_matching_weight_profile_page' AND NON_UNIQUE=1
     AND INDEX_TYPE='BTREE' AND IS_VISIBLE='YES' AND SUB_PART IS NULL AND ((SEQ_IN_INDEX=1 AND COLUMN_NAME='update_time' AND COLLATION='D') OR (SEQ_IN_INDEX=2 AND COLUMN_NAME='id' AND COLLATION='D')))=2,
 'DO 0','SELECT `existing ix_matching_weight_profile_page has an incompatible definition`'));
PREPARE hr_scope_stmt FROM @hr_scope_sql;
EXECUTE hr_scope_stmt;
DEALLOCATE PREPARE hr_scope_stmt;
SET @hr_scope_sql = NULL;
